package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.ApprovalStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApprovalTaskResponse(
        String approvalId,
        String ticketId,
        String planId,
        ApprovalStatus status,
        String approvalType,
        String requestedBy,
        String requestedReason,
        String approverId,
        String approverComment,
        Map<String, Object> plan,
        List<Map<String, Object>> approvalSteps,
        Instant createdAt,
        Instant decidedAt,
        Instant updatedAt) {
}
