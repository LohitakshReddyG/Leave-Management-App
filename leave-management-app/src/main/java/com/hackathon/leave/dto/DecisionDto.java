package com.hackathon.leave.dto;

import jakarta.validation.constraints.NotNull;

public record DecisionDto(
        @NotNull Long actorId,
        String comment) {
}
