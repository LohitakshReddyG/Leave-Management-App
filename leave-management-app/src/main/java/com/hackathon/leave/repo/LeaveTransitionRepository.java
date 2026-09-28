package com.hackathon.leave.repo;

import com.hackathon.leave.model.LeaveTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveTransitionRepository extends JpaRepository<LeaveTransition, Long> {

    List<LeaveTransition> findByRequestIdOrderByAtAscIdAsc(Long requestId);
}
