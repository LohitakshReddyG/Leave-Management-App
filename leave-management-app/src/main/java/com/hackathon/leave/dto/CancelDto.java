package com.hackathon.leave.dto;

import jakarta.validation.constraints.NotNull;

public record CancelDto(
        @NotNull Long actorId) {
}
