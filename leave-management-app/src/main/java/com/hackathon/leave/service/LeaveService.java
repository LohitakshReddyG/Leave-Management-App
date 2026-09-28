package com.hackathon.leave.service;

import com.hackathon.leave.model.Employee;
import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.model.LeaveType;
import com.hackathon.leave.model.RequestStatus;
import com.hackathon.leave.repo.EmployeeRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.LeaveTypeRepository;
import com.hackathon.leave.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Business logic: working-day counts, pro-rated entitlement, balances and the
 * apply flow.
 */
@Service
public class LeaveService {

    /** What getBalance returns; the controller maps it to the JSON BalanceView. */
    public record BalanceSummary(String leaveTypeCode, int year,
                                 BigDecimal entitledDays, int usedDays, BigDecimal remainingDays) {
    }

    private final LeaveRequestRepository requestRepo;
    private final EmployeeRepository employeeRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final ConflictService conflictService;
    private final ApprovalService approvalService;

    public LeaveService(LeaveRequestRepository requestRepo,
                        EmployeeRepository employeeRepo,
                        LeaveTypeRepository leaveTypeRepo,
                        ConflictService conflictService,
                        ApprovalService approvalService) {
        this.requestRepo = requestRepo;
        this.employeeRepo = employeeRepo;
        this.leaveTypeRepo = leaveTypeRepo;
        this.conflictService = conflictService;
        this.approvalService = approvalService;
    }

    /** Working days between start and end inclusive, weekends excluded. */
    public static int workingDays(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return 0;
        }
        int days = 0;
        for (LocalDate cur = start; !cur.isAfter(end); cur = cur.plusDays(1)) {
            if (cur.getDayOfWeek() != DayOfWeek.SATURDAY && cur.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return days;
    }

    /**
     * Pro-rated entitlement for the given year:
     *   full quota once the joining year has passed,
     *   quota × (months from the joining month through December) / 12 in the joining year,
     *   rounded half-up to one decimal.
     * Kiran joins 15 September with an ANNUAL quota of 20 → 20 × 4 / 12 = 6.7 days.
     */
    public static BigDecimal entitledDays(LocalDate joining, int quotaDays, int year) {
        if (joining == null) {
            return BigDecimal.valueOf(quotaDays);
        }
        if (joining.getYear() > year) {
            return BigDecimal.ZERO;
        }
        if (joining.getYear() < year) {
            return BigDecimal.valueOf(quotaDays);
        }
        long monthsFromJoinInclusive = 12 - joining.getMonthValue() + 1;
        return BigDecimal.valueOf(quotaDays)
                .multiply(BigDecimal.valueOf(monthsFromJoinInclusive))
                .divide(BigDecimal.valueOf(12), 1, RoundingMode.HALF_UP);
    }

    /**
     * The apply flow: validate, compute days, check balance (pending requests
     * count as in-flight so two concurrent applies cannot double-spend), create
     * the request, flag conflicts, and open the audit trail.
     */
    @Transactional
    public LeaveRequest apply(Long employeeId, String leaveTypeCode,
                              LocalDate start, LocalDate end, String reason) {
        Employee emp = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ApiException(404, "Employee not found: " + employeeId));
        LeaveType type = leaveTypeRepo.findByCode(leaveTypeCode);
        if (type == null) {
            throw new ApiException(400, "Unknown leave type: " + leaveTypeCode);
        }
        if (end.isBefore(start)) {
            throw new ApiException(400, "End date cannot be before the start date");
        }
        LocalDate today = LocalDate.now();
        if (start.isBefore(today)) {
            throw new ApiException(400, "Leave cannot start in the past");
        }

        int days = workingDays(start, end);
        if (days == 0) {
            throw new ApiException(400, "The range covers no working days (weekend-only)");
        }

        BalanceSummary balance = getBalance(employeeId, leaveTypeCode, start.getYear());
        if (BigDecimal.valueOf(days).compareTo(balance.remainingDays()) > 0) {
            throw new ApiException(400, "Insufficient balance: " + days + " working day(s) requested, "
                    + balance.remainingDays() + " remaining of " + balance.entitledDays() + " entitled");
        }

        LeaveRequest r = new LeaveRequest();
        r.setEmployeeId(employeeId);
        r.setLeaveTypeId(type.getId());
        r.setStartDate(start);
        r.setEndDate(end);
        r.setDays(days);
        r.setReason(reason);
        r.setStatus(RequestStatus.PENDING_MANAGER);
        Instant now = Instant.now();
        r.setCreatedAt(now);
        r.setPendingSince(now);
        r = requestRepo.save(r);

        conflictService.flagIfConflicted(r);
        approvalService.recordTransition(r.getId(), employeeId, null,
                RequestStatus.PENDING_MANAGER, "leave applied: " + days + " working day(s)");
        return r;
    }

    /**
     * Computed, never stored: entitled minus the days already held by active
     * requests (approved or pending) that touch the given year.
     */
    @Transactional(readOnly = true)
    public BalanceSummary getBalance(Long employeeId, String leaveTypeCode, int year) {
        Employee emp = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ApiException(404, "Employee not found: " + employeeId));
        LeaveType type = leaveTypeRepo.findByCode(leaveTypeCode);
        if (type == null) {
            throw new ApiException(400, "Unknown leave type: " + leaveTypeCode);
        }

        BigDecimal entitled = entitledDays(emp.getJoiningDate(), type.getAnnualQuotaDays(), year);

        List<LeaveRequest> active = requestRepo.findByEmployeeId(employeeId).stream()
                .filter(r -> RequestStatus.ACTIVE_STATUSES.contains(r.getStatus()))
                .filter(r -> r.getStartDate().getYear() <= year && r.getEndDate().getYear() >= year)
                .toList();
        int used = active.stream().mapToInt(LeaveRequest::getDays).sum();

        return new BalanceSummary(leaveTypeCode, year, entitled, used,
                entitled.subtract(BigDecimal.valueOf(used)).max(BigDecimal.ZERO));
    }

    /** Months since joining, used by the frontend's balance card. */
    @Transactional(readOnly = true)
    public long monthsSinceJoining(Long employeeId) {
        Employee emp = employeeRepo.findById(employeeId).orElse(null);
        if (emp == null || emp.getJoiningDate() == null) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(emp.getJoiningDate().withDayOfMonth(1),
                LocalDate.now().withDayOfMonth(1));
    }

}
