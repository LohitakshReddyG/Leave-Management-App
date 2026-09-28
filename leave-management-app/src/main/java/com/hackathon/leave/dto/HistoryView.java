package com.hackathon.leave.dto;

import java.time.Instant;

public record HistoryView(
        String fromStatus,
        String toStatus,
        String actorName,
        String comment,
        Instant at) {
}
