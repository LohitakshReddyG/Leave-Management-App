package com.hackathon.leave.service;

import com.hackathon.leave.model.Employee;
import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.model.Notification;
import com.hackathon.leave.model.RequestStatus;
import com.hackathon.leave.model.Role;
import com.hackathon.leave.repo.EmployeeRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.LeaveTransitionRepository;
import com.hackathon.leave.repo.NotificationRepository;
import com.hackathon.leave.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The approval workflow. Every status change goes through transition(),
 * which validates the move against the RequestStatus state map and writes
 * an audit row. Illegal moves throw 409.
 */
@Service
public class ApprovalService {

    private final LeaveRequestRepository requestRepo;
    private final LeaveTransitionRepository transitionRepo;
    private final NotificationRepository notificationRepo;
    private final EmployeeRepository employeeRepo;
    private final ConflictService conflictService;

    public ApprovalService(LeaveRequestRepository requestRepo,
                            LeaveTransitionRepository transitionRepo,
                            NotificationRepository notificationRepo,
                            EmployeeRepository employeeRepo,
                            ConflictService conflictService) {
        this.requestRepo = requestRepo;
        this.transitionRepo = transitionRepo;
        this.notificationRepo = notificationRepo;
        this.employeeRepo = employeeRepo;
        this.conflictService = conflictService;
    }

    /**
     * Manager decides at PENDING_MANAGER; HR decides at PENDING_HR.
     * Approve at the manager stage advances to HR; approve at the HR stage
     * finalises the request.
     */
    @Transactional
    public LeaveRequest decide(Long requestId, Long actorId, boolean approve, String comment) {
        LeaveRequest r = requestRepo.findById(requestId)
                .orElseThrow(() -> new ApiException(404, "Request not found: " + requestId));
        Employee actor = employeeRepo.findById(actorId)
                .orElseThrow(() -> new ApiException(404, "Actor not found: " + actorId));

        boolean managerStage = r.getStatus() == RequestStatus.PENDING_MANAGER;
        boolean hrStage = r.getStatus() == RequestStatus.PENDING_HR;

        if (!managerStage && !hrStage) {
            throw new ApiException(409,
                    "Request " + requestId + " is not pending a decision: " + r.getStatus());
        }

        if (managerStage) {
            Employee owner = employeeRepo.findById(r.getEmployeeId())
                    .orElseThrow(() -> new ApiException(404, "Requesting employee not found"));
            if (actor.getRole() != Role.MANAGER || !actor.getId().equals(owner.getManagerId())) {
                throw new ApiException(403, "Only the requesting employee's manager decides at this stage");
            }
        } else {
            if (actor.getRole() != Role.HR) {
                throw new ApiException(403, "Only HR decides at this stage");
            }
        }

        RequestStatus target = managerStage
                ? (approve ? RequestStatus.PENDING_HR : RequestStatus.REJECTED_BY_MANAGER)
                : (approve ? RequestStatus.APPROVED : RequestStatus.REJECTED_BY_HR);

        // Team state may have changed since the request was filed: refresh the
        // conflict flag before the request moves on to HR.
        if (managerStage && approve) {
            conflictService.flagIfConflicted(r);
        }

        return transition(r, target, actorId, comment);
    }

    /** The employee can cancel their own request while it is still pending. */
    @Transactional
    public LeaveRequest cancel(Long requestId, Long actorId) {
        LeaveRequest r = requestRepo.findById(requestId)
                .orElseThrow(() -> new ApiException(404, "Request not found: " + requestId));
        if (!r.getEmployeeId().equals(actorId)) {
            throw new ApiException(403, "Only the requesting employee can cancel");
        }
        if (!r.getStatus().isPending()) {
            throw new ApiException(409, "Request " + requestId + " can no longer be cancelled: " + r.getStatus());
        }
        return transition(r, RequestStatus.CANCELLED, actorId, "cancelled by employee");
    }

    /** Raw audit-row writer, used for the initial application and by the escalation scheduler. */
    public void recordTransition(Long requestId, Long actorId, RequestStatus from,
                                 RequestStatus to, String comment) {
        transitionRepo.save(new com.hackathon.leave.model.LeaveTransition(
                requestId, actorId, from, to, comment, Instant.now()));
    }

    private LeaveRequest transition(LeaveRequest r, RequestStatus target, Long actorId, String comment) {
        if (!r.getStatus().canTransitionTo(target)) {
            throw new ApiException(409, "Illegal transition " + r.getStatus() + " → " + target);
        }
        RequestStatus from = r.getStatus();
        r.setStatus(target);
        if (target == RequestStatus.PENDING_HR) {
            r.setPendingSince(Instant.now()); // restart the clock for the HR stage
        }
        recordTransition(r.getId(), actorId, from, target, comment);

        if (target == RequestStatus.APPROVED || target == RequestStatus.REJECTED_BY_MANAGER
                || target == RequestStatus.REJECTED_BY_HR) {
            notificationRepo.save(new Notification(r.getId(), target.name(),
                    "Request " + r.getId() + " " + target.name().toLowerCase().replace('_', ' ')
                            + (comment != null && !comment.isBlank() ? " — " + comment : ""),
                    Instant.now()));
        }
        return requestRepo.save(r);
    }
}
