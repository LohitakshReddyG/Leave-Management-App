package com.hackathon.leave.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * In-app inbox: escalations, conflict flags, decisions. The mock email log of
 * the production system.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long requestId;

    /** ESCALATION, CONFLICT, APPROVED, REJECTED, INFO. */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    public Notification() {
    }

    public Notification(Long requestId, String type, String message, Instant createdAt) {
        this.requestId = requestId;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
