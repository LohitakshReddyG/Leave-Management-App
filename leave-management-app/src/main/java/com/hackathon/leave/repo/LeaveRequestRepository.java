package com.hackathon.leave.repo;

import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeId(Long employeeId);

    List<LeaveRequest> findByStatus(RequestStatus status);

    List<LeaveRequest> findByStatusAndPendingSinceBefore(RequestStatus status, Instant cutoff);

    List<LeaveRequest> findByEmployeeIdInAndStatus(Collection<Long> employeeIds, RequestStatus status);

    /** Count of the employee's active (pending or approved) leaves that cover the given day. */
    @Query("SELECT COUNT(r) FROM LeaveRequest r " +
           "WHERE r.employeeId = :empId AND r.status IN :statuses " +
           "AND r.startDate <= :day AND r.endDate >= :day")
    long countActiveOn(@Param("empId") Long empId,
                      @Param("day") LocalDate day,
                      @Param("statuses") Collection<RequestStatus> statuses);
}
