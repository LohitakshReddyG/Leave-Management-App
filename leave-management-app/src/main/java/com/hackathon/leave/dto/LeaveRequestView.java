package com.hackathon.leave.dto;

import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.model.LeaveType;

import java.time.Instant;
import java.time.LocalDate;

public record LeaveRequestView(
        Long id,
        String employeeName,
        String leaveTypeCode,
        LocalDate start,
        LocalDate end,
        int days,
        String reason,
        String status,
        boolean conflictFlag,
        String conflictDetail,
        Instant createdAt) {

    public static LeaveRequestView of(LeaveRequest r, String employeeName, LeaveType type) {
        return new LeaveRequestView(
                r.getId(),
                employeeName,
                type != null ? type.getCode() : null,
                r.getStartDate(),
                r.getEndDate(),
                r.getDays(),
                r.getReason(),
                r.getStatus().name(),
                r.isConflictFlag(),
                r.getConflictDetail(),
                r.getCreatedAt());
    }
}
