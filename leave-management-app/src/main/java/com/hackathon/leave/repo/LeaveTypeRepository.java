package com.hackathon.leave.repo;

import com.hackathon.leave.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    LeaveType findByCode(String code);
}
