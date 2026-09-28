package com.hackathon.leave.repo;

import com.hackathon.leave.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByTeamId(Long teamId);

    List<Employee> findByManagerId(Long managerId);
}
