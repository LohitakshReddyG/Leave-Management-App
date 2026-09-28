package com.hackathon.leave.dto;

import com.hackathon.leave.model.LeaveType;

public record LeaveTypeView(String code, int annualQuotaDays) {

    public static LeaveTypeView of(LeaveType t) {
        return new LeaveTypeView(t.getCode(), t.getAnnualQuotaDays());
    }
}
