package com.hackathon.leave.dto;

import java.math.BigDecimal;

public record BalanceView(
        String leaveTypeCode,
        int year,
        BigDecimal entitledDays,
        int usedDays,
        BigDecimal remainingDays) {
}
