package com.hackathon.leave.dto;

import java.time.Instant;

public record NotificationView(
        Long id,
        Long requestId,
        String type,
        String message,
        Instant createdAt) {
}
