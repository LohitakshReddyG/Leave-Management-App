package com.hackathon.leave.web;

import com.hackathon.leave.dto.ApplyRequestDto;
import com.hackathon.leave.dto.BalanceView;
import com.hackathon.leave.dto.EmployeeView;
import com.hackathon.leave.dto.HistoryView;
import com.hackathon.leave.dto.LeaveRequestView;
import com.hackathon.leave.dto.LeaveTypeView;
import com.hackathon.leave.dto.NotificationView;
import com.hackathon.leave.model.Employee;
import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.model.LeaveType;
import com.hackathon.leave.model.RequestStatus;
import com.hackathon.leave.repo.EmployeeRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.LeaveTransitionRepository;
import com.hackathon.leave.repo.LeaveTypeRepository;
import com.hackathon.leave.repo.NotificationRepository;
import com.hackathon.leave.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class LeaveController {

    private final LeaveService leaveService;
    private final LeaveRequestRepository requestRepo;
    private final EmployeeRepository employeeRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final LeaveTransitionRepository transitionRepo;
    private final NotificationRepository notificationRepo;

    public LeaveController(LeaveService leaveService,
                           LeaveRequestRepository requestRepo,
                           EmployeeRepository employeeRepo,
                           LeaveTypeRepository leaveTypeRepo,
                           LeaveTransitionRepository transitionRepo,
                           NotificationRepository notificationRepo) {
        this.leaveService = leaveService;
        this.requestRepo = requestRepo;
        this.employeeRepo = employeeRepo;
        this.leaveTypeRepo = leaveTypeRepo;
        this.transitionRepo = transitionRepo;
        this.notificationRepo = notificationRepo;
    }

    @PostMapping("/requests")
    public ResponseEntity<LeaveRequestView> apply(@Valid @RequestBody ApplyRequestDto dto) {
        LeaveRequest r = leaveService.apply(dto.employeeId(), dto.leaveTypeCode(),
                dto.start(), dto.end(), dto.reason());
        return ResponseEntity.status(201).body(view(r));
    }

    @GetMapping("/requests")
    public List<LeaveRequestView> myRequests(@RequestParam Long employeeId) {
        return requestRepo.findByEmployeeId(employeeId).stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(this::view)
                .toList();
    }

    @GetMapping("/requests/pending/manager")
    public List<LeaveRequestView> managerQueue(@RequestParam Long managerId) {
        List<Long> teamIds = employeeRepo.findByManagerId(managerId).stream()
                .map(Employee::getId).toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return requestRepo.findByEmployeeIdInAndStatus(teamIds, RequestStatus.PENDING_MANAGER)
                .stream().map(this::view).toList();
    }

    @GetMapping("/requests/pending/hr")
    public List<LeaveRequestView> hrQueue() {
        return requestRepo.findByStatus(RequestStatus.PENDING_HR).stream().map(this::view).toList();
    }

    @GetMapping("/requests/{id}/history")
    public List<HistoryView> history(@PathVariable Long id) {
        Map<Long, String> names = employeeRepo.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        return transitionRepo.findByRequestIdOrderByAtAscIdAsc(id).stream()
                .map(t -> new HistoryView(
                        t.getFromStatus() != null ? t.getFromStatus().name() : null,
                        t.getToStatus().name(),
                        t.getActorId() == null ? "system" : names.getOrDefault(t.getActorId(), "unknown"),
                        t.getComment(),
                        t.getAt()))
                .toList();
    }

    @GetMapping("/employees")
    public List<EmployeeView> employees(@RequestParam(required = false) Long teamId) {
        List<Employee> list = teamId != null ? employeeRepo.findByTeamId(teamId) : employeeRepo.findAll();
        return list.stream().map(EmployeeView::of).toList();
    }

    @GetMapping("/employees/{id}/balance")
    public BalanceView balance(@PathVariable Long id,
                               @RequestParam(defaultValue = "2026") int year,
                               @RequestParam(defaultValue = "ANNUAL") String type) {
        var b = leaveService.getBalance(id, type, year);
        return new BalanceView(b.leaveTypeCode(), b.year(), b.entitledDays(),
                b.usedDays(), b.remainingDays());
    }

    @GetMapping("/leave-types")
    public List<LeaveTypeView> leaveTypes() {
        return leaveTypeRepo.findAll().stream().map(LeaveTypeView::of).toList();
    }

    /**
     * Notification inbox. ?hr=true returns escalations and conflict flags for
     * the HR view; ?managerId= returns notifications about that manager's team.
     */
    @GetMapping("/notifications")
    public List<NotificationView> notifications(@RequestParam(required = false) Long managerId,
                                                @RequestParam(required = false, defaultValue = "false") boolean hr) {
        if (hr) {
            return notificationRepo.findTop50ByTypeInOrderByCreatedAtDesc(List.of("ESCALATION", "CONFLICT"))
                    .stream().map(this::notificationView).toList();
        }
        if (managerId != null) {
            List<Long> requestIds = employeeRepo.findByManagerId(managerId).stream()
                    .flatMap(m -> requestRepo.findByEmployeeId(m.getId()).stream())
                    .map(LeaveRequest::getId).toList();
            if (requestIds.isEmpty()) {
                return List.of();
            }
            return notificationRepo.findTop50ByRequestIdInOrderByCreatedAtDesc(requestIds)
                    .stream().map(this::notificationView).toList();
        }
        return notificationRepo.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(50).map(this::notificationView).toList();
    }

    // ---------- view helpers ----------

    private LeaveRequestView view(LeaveRequest r) {
        Map<Long, String> names = employeeRepo.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        Map<Long, LeaveType> types = leaveTypeRepo.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, Function.identity()));
        return LeaveRequestView.of(r, names.get(r.getEmployeeId()), types.get(r.getLeaveTypeId()));
    }

    private NotificationView notificationView(com.hackathon.leave.model.Notification n) {
        return new NotificationView(n.getId(), n.getRequestId(), n.getType(), n.getMessage(), n.getCreatedAt());
    }
}
