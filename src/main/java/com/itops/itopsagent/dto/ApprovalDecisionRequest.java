package com.itops.itopsagent.dto;

public record ApprovalDecisionRequest(
        String approverId,
        String comment) {
}
