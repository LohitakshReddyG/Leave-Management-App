package com.hackathon.leave.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "leave_request")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private Long leaveTypeId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** Working days between start and end, weekends excluded. */
    @Column(nullable = false)
    private int days;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /** True when teammates already hold leave on any day of this range. Flag only — never auto-reject. */
    @Column(nullable = false)
    private boolean conflictFlag;

    /** Human-readable detail: which days clash and who is already off. */
    private String conflictDetail;

    @Column(nullable = false)
    private Instant createdAt;

    /** When the request entered its current pending stage; drives the escalation timeout. */
    @Column(nullable = false)
    private Instant pendingSince;

    /** Optimistic locking: two concurrent decisions on the same request cannot both apply. */
    @Version
    private Long version;

    public LeaveRequest() {
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getLeaveTypeId() {
        return leaveTypeId;
    }

    public void setLeaveTypeId(Long leaveTypeId) {
        this.leaveTypeId = leaveTypeId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public boolean isConflictFlag() {
        return conflictFlag;
    }

    public void setConflictFlag(boolean conflictFlag) {
        this.conflictFlag = conflictFlag;
    }

    public String getConflictDetail() {
        return conflictDetail;
    }

    public void setConflictDetail(String conflictDetail) {
        this.conflictDetail = conflictDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPendingSince() {
        return pendingSince;
    }

    public void setPendingSince(Instant pendingSince) {
        this.pendingSince = pendingSince;
    }
}
