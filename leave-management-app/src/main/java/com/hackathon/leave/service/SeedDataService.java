package com.hackathon.leave.service;

import com.hackathon.leave.model.Employee;
import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.model.LeaveTransition;
import com.hackathon.leave.model.LeaveType;
import com.hackathon.leave.model.RequestStatus;
import com.hackathon.leave.model.Role;
import com.hackathon.leave.repo.EmployeeRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.LeaveTransitionRepository;
import com.hackathon.leave.repo.LeaveTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The demo dataset. Idempotent — safe on every restart.
 *
 * Team 1: Priya (manager), Arjun, Meera, Kiran (joined 15 Sep 2026 — the
 * pro-rating showcase: 6.7 of 20 annual days), Ravi. Divya is HR.
 * Arjun and Meera hold pre-approved overlapping leaves in the first week of
 * October, so any new request for that week from the team is conflict-flagged.
 */
@Component
public class SeedDataService implements CommandLineRunner {

    private final EmployeeRepository employeeRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final LeaveRequestRepository requestRepo;
    private final LeaveTransitionRepository transitionRepo;

    public SeedDataService(EmployeeRepository employeeRepo,
                           LeaveTypeRepository leaveTypeRepo,
                           LeaveRequestRepository requestRepo,
                           LeaveTransitionRepository transitionRepo) {
        this.employeeRepo = employeeRepo;
        this.leaveTypeRepo = leaveTypeRepo;
        this.requestRepo = requestRepo;
        this.transitionRepo = transitionRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (employeeRepo.count() > 0) {
            return; // already seeded
        }

        LeaveType annual = leaveTypeRepo.save(new LeaveType("ANNUAL", 20));
        leaveTypeRepo.save(new LeaveType("SICK", 10));
        leaveTypeRepo.save(new LeaveType("CASUAL", 8));

        Employee priya = employeeRepo.save(new Employee("Priya", "priya@clockit.dev", Role.MANAGER, null, 1L, LocalDate.of(2019, 1, 1)));
        Employee arjun = employeeRepo.save(new Employee("Arjun", "arjun@clockit.dev", Role.EMPLOYEE, priya.getId(), 1L, LocalDate.of(2020, 1, 1)));
        employeeRepo.save(new Employee("Meera", "meera@clockit.dev", Role.EMPLOYEE, priya.getId(), 1L, LocalDate.of(2021, 1, 1)));
        employeeRepo.save(new Employee("Kiran", "kiran@clockit.dev", Role.EMPLOYEE, priya.getId(), 1L, LocalDate.of(2026, 9, 15)));
        employeeRepo.save(new Employee("Ravi", "ravi@clockit.dev", Role.EMPLOYEE, priya.getId(), 1L, LocalDate.of(2022, 1, 1)));
        employeeRepo.save(new Employee("Divya", "divya@clockit.dev", Role.HR, null, null, LocalDate.of(2018, 1, 1)));

        Instant filed = Instant.now().minusSeconds(10 * 24 * 3600);
        Instant approved = Instant.now().minusSeconds(9 * 24 * 3600);
        seedApprovedLeave(arjun, annual, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9), filed, approved);
        seedApprovedLeave(employeeRepo.findByTeamId(1L).stream()
                        .filter(e -> e.getName().equals("Meera")).findFirst().orElseThrow(),
                annual, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8), filed, approved);
    }

    private void seedApprovedLeave(Employee emp, LeaveType type, LocalDate start, LocalDate end,
                                   Instant filed, Instant decided) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployeeId(emp.getId());
        r.setLeaveTypeId(type.getId());
        r.setStartDate(start);
        r.setEndDate(end);
        r.setDays(LeaveService.workingDays(start, end));
        r.setReason("Pre-approved leave");
        r.setStatus(RequestStatus.APPROVED);
        r.setCreatedAt(filed);
        r.setPendingSince(filed);
        r = requestRepo.save(r);

        transitionRepo.save(new LeaveTransition(r.getId(), emp.getId(), null, RequestStatus.PENDING_MANAGER, "leave applied", filed));
        transitionRepo.save(new LeaveTransition(r.getId(), 1L, RequestStatus.PENDING_MANAGER, RequestStatus.PENDING_HR, "manager approved", filed.plusSeconds(3600)));
        transitionRepo.save(new LeaveTransition(r.getId(), 6L, RequestStatus.PENDING_HR, RequestStatus.APPROVED, "hr approved", decided));
    }
}
