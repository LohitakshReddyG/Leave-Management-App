package com.hackathon.leave.web;

import com.hackathon.leave.dto.CancelDto;
import com.hackathon.leave.dto.DecisionDto;
import com.hackathon.leave.dto.LeaveRequestView;
import com.hackathon.leave.model.LeaveRequest;
import com.hackathon.leave.repo.EmployeeRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.LeaveTypeRepository;
import com.hackathon.leave.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final LeaveRequestRepository requestRepo;
    private final EmployeeRepository employeeRepo;
    private final LeaveTypeRepository leaveTypeRepo;

    public ApprovalController(ApprovalService approvalService,
                              LeaveRequestRepository requestRepo,
                              EmployeeRepository employeeRepo,
                              LeaveTypeRepository leaveTypeRepo) {
        this.approvalService = approvalService;
        this.requestRepo = requestRepo;
        this.employeeRepo = employeeRepo;
        this.leaveTypeRepo = leaveTypeRepo;
    }

    @PostMapping("/{id}/approve")
    public LeaveRequestView approve(@PathVariable Long id, @Valid @RequestBody DecisionDto dto) {
        return view(approvalService.decide(id, dto.actorId(), true, dto.comment()));
    }

    @PostMapping("/{id}/reject")
    public LeaveRequestView reject(@PathVariable Long id, @Valid @RequestBody DecisionDto dto) {
        return view(approvalService.decide(id, dto.actorId(), false, dto.comment()));
    }

    @PostMapping("/{id}/cancel")
    public LeaveRequestView cancel(@PathVariable Long id, @Valid @RequestBody CancelDto dto) {
        return view(approvalService.cancel(id, dto.actorId()));
    }

    private LeaveRequestView view(LeaveRequest r) {
        String name = employeeRepo.findById(r.getEmployeeId())
                .map(e -> e.getName()).orElse("unknown");
        var type = leaveTypeRepo.findById(r.getLeaveTypeId()).orElse(null);
        return LeaveRequestView.of(r, name, type);
    }
}
