package com.hackathon.leave.service;

import com.hackathon.leave.config.AppProperties;
import com.hackathon.leave.model.Employee;
import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.model.Notification;
import com.hackathon.leave.model.RequestStatus;
import com.hackathon.leave.repo.EmployeeRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Team-wide conflict detection. Scans every day of the requested range and
 * flags the request when the number of teammates already off on any day
 * reaches the configured threshold.
 *
 * The result is a FLAG only — the request is never auto-rejected. The approver
 * sees the flag and the detail (who is off, which days) and decides.
 */
@Service
public class ConflictService {

    private final EmployeeRepository employeeRepo;
    private final LeaveRequestRepository requestRepo;
    private final NotificationRepository notificationRepo;
    private final AppProperties appProperties;

    public ConflictService(EmployeeRepository employeeRepo,
                           LeaveRequestRepository requestRepo,
                           NotificationRepository notificationRepo,
                           AppProperties appProperties) {
        this.employeeRepo = employeeRepo;
        this.requestRepo = requestRepo;
        this.notificationRepo = notificationRepo;
        this.appProperties = appProperties;
    }

    /** Recomputes the conflict flag from scratch: clears it, scans, and sets it if needed. */
    @Transactional
    public void flagIfConflicted(LeaveRequest r) {
        boolean wasFlagged = r.isConflictFlag();
        r.setConflictFlag(false);
        r.setConflictDetail(null);

        Employee me = employeeRepo.findById(r.getEmployeeId()).orElse(null);
        if (me == null || me.getTeamId() == null) {
            return;
        }

        List<Employee> team = employeeRepo.findByTeamId(me.getTeamId());
        int threshold = appProperties.getConflict().getMaxSimultaneous();
        Map<LocalDate, List<String>> overLimit = new LinkedHashMap<>();

        for (LocalDate day = r.getStartDate(); !day.isAfter(r.getEndDate()); day = day.plusDays(1)) {
            LocalDate d = day;
            List<String> off = new ArrayList<>();
            for (Employee m : team) {
                if (m.getId().equals(r.getEmployeeId())) {
                    continue;
                }
                if (requestRepo.countActiveOn(m.getId(), d, RequestStatus.ACTIVE_STATUSES) > 0) {
                    off.add(m.getName());
                }
            }
            if (off.size() >= threshold) {
                overLimit.put(d, off);
            }
        }

        if (overLimit.isEmpty()) {
            return;
        }

        String detail = "Team conflict on " + overLimit.size() + " day(s): "
                + overLimit.entrySet().stream()
                        .map(e -> e.getKey() + " → off: " + e.getValue())
                        .collect(Collectors.joining("; "));

        r.setConflictFlag(true);
        r.setConflictDetail(detail);
        if (!wasFlagged) {
            // notify only on a newly flagged conflict — the approval-time re-check
            // refreshes the flag without spamming the inbox again
            notificationRepo.save(new Notification(r.getId(), "CONFLICT",
                    "Request " + r.getId() + " conflicts with teammates — " + detail, Instant.now()));
        }
    }
}
