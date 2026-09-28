package com.hackathon.leave.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The audit trail of the state machine. One row is written for every status
 * change, including the automatic escalation ones (actorId null = system).
 */
@Entity
@Table(name = "leave_transition")
public class LeaveTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    /** Who made the change; null means the system (escalation). */
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private RequestStatus toStatus;

    private String comment;

    @Column(nullable = false)
    private Instant at;

    public LeaveTransition() {
    }

    public LeaveTransition(Long requestId, Long actorId, RequestStatus fromStatus,
                            RequestStatus toStatus, String comment, Instant at) {
        this.requestId = requestId;
        this.actorId = actorId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.at = at;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getActorId() {
        return actorId;
    }

    public RequestStatus getFromStatus() {
        return fromStatus;
    }

    public RequestStatus getToStatus() {
        return toStatus;
    }

    public String getComment() {
        return comment;
    }

    public Instant getAt() {
        return at;
    }
}
