package com.hackathon.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ApplyRequestDto(
        @NotNull Long employeeId,
        @NotBlank String leaveTypeCode,
        @NotNull LocalDate start,
        @NotNull LocalDate end,
        String reason) {
}
