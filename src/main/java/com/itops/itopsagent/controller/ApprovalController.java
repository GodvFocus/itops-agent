package com.itops.itopsagent.controller;

import com.itops.itopsagent.dto.ApprovalDecisionRequest;
import com.itops.itopsagent.dto.ApprovalTaskResponse;
import com.itops.itopsagent.service.ApprovalCommandService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalCommandService approvalCommandService;

    @GetMapping
    public List<ApprovalTaskResponse> listApprovals(@RequestParam(required = false) String ticketId) {
        return approvalCommandService.listApprovals(ticketId);
    }

    @PostMapping("/{approvalId}/approve")
    public ApprovalTaskResponse approve(@PathVariable String approvalId, @RequestBody(required = false) ApprovalDecisionRequest request) {
        return approvalCommandService.approve(approvalId, request);
    }

    @PostMapping("/{approvalId}/reject")
    public ApprovalTaskResponse reject(@PathVariable String approvalId, @RequestBody(required = false) ApprovalDecisionRequest request) {
        return approvalCommandService.reject(approvalId, request);
    }
}
