package com.hackathon.leave.dto;

import com.hackathon.leave.model.Employee;

import java.time.LocalDate;

public record EmployeeView(
        Long id,
        String name,
        String email,
        String role,
        Long managerId,
        Long teamId,
        LocalDate joiningDate) {

    public static EmployeeView of(Employee e) {
        return new EmployeeView(e.getId(), e.getName(), e.getEmail(),
                e.getRole().name(), e.getManagerId(), e.getTeamId(), e.getJoiningDate());
    }
}
