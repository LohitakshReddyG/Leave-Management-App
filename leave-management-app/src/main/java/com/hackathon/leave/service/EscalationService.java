package com.hackathon.leave.service;

import com.hackathon.leave.config.AppProperties;
import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.model.Notification;
import com.hackathon.leave.model.RequestStatus;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Automatic escalation. A scheduled scan picks up requests that have sat at
 * PENDING_MANAGER longer than the configured timeout, walks them through the
 * ESCALATED pass-through state (both moves are recorded on the audit trail),
 * and routes them to the HR queue with a notification.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final LeaveRequestRepository requestRepo;
    private final NotificationRepository notificationRepo;
    private final ApprovalService approvalService;
    private final AppProperties appProperties;

    public EscalationService(LeaveRequestRepository requestRepo,
                             NotificationRepository notificationRepo,
                             ApprovalService approvalService,
                             AppProperties appProperties) {
        this.requestRepo = requestRepo;
        this.notificationRepo = notificationRepo;
        this.approvalService = approvalService;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void escalateStaleRequests() {
        long timeoutSeconds = appProperties.getEscalation().getTimeoutSeconds();
        Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
        List<LeaveRequest> stale =
                requestRepo.findByStatusAndPendingSinceBefore(RequestStatus.PENDING_MANAGER, cutoff);

        for (LeaveRequest r : stale) {
            r.setStatus(RequestStatus.ESCALATED);
            approvalService.recordTransition(r.getId(), null, RequestStatus.PENDING_MANAGER,
                    RequestStatus.ESCALATED, "auto: manager did not act within " + timeoutSeconds + "s");

            r.setStatus(RequestStatus.PENDING_HR);
            r.setPendingSince(Instant.now());
            approvalService.recordTransition(r.getId(), null, RequestStatus.ESCALATED,
                    RequestStatus.PENDING_HR, "auto: routed to HR after escalation");

            notificationRepo.save(new Notification(r.getId(), "ESCALATION",
                    "Request " + r.getId() + " escalated — manager did not act within "
                            + timeoutSeconds + "s; routed to HR", Instant.now()));
            requestRepo.save(r);
            log.info("Escalated stale request {} to HR", r.getId());
        }
    }
}
