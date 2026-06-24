package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.dto.ApprovalDecisionRequest;
import com.itops.itopsagent.dto.ApprovalTaskResponse;
import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import com.itops.itopsagent.entity.enums.ApprovalStatus;
import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.service.ApprovalCommandService;
import com.itops.itopsagent.service.ApprovalTaskStoreService;
import com.itops.itopsagent.service.ConversationMessageService;
import com.itops.itopsagent.service.TicketService;
import com.itops.itopsagent.service.harness.HarnessPlanValidationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApprovalCommandServiceImpl implements ApprovalCommandService {

    private final ApprovalTaskStoreService approvalTaskStoreService;
    private final TicketService ticketService;
    private final ConversationMessageService conversationMessageService;
    private final HarnessPlanValidationService harnessPlanValidationService;

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalTaskResponse> listApprovals(String ticketId) {
        return ticketId == null || ticketId.isBlank()
                ? approvalTaskStoreService.listAll()
                : approvalTaskStoreService.listByTicketId(ticketId);
    }

    @Override
    @Transactional
    public ApprovalTaskResponse approve(String approvalId, ApprovalDecisionRequest request) {
        ApprovalTaskResponse current = approvalTaskStoreService.getByApprovalId(approvalId);
        if (current.status() != ApprovalStatus.PENDING) {
            return current;
        }
        String approverId = normalizeApproverId(request);
        CandidatePlanRequest plan = approvalTaskStoreService.getPlan(approvalId);
        ticketService.transitionStatus(
                current.ticketId(),
                new TransitionTicketStatusRequest(TicketStatus.EXECUTING, approverId, UserRole.APPROVER, null, safeComment(request, "审批通过，恢复执行")));
        ApprovalTaskResponse updated = approvalTaskStoreService.markApproved(approvalId, approverId, request == null ? null : request.comment());
        conversationMessageService.appendMessageIfChanged(
                current.ticketId(),
                ConversationRole.AGENT,
                ConversationMessageType.AGENT_SUMMARY,
                "审批已通过，Harness 正在恢复执行高风险步骤。");
        harnessPlanValidationService.resumeApprovedPlan(plan);
        return updated;
    }

    @Override
    @Transactional
    public ApprovalTaskResponse reject(String approvalId, ApprovalDecisionRequest request) {
        ApprovalTaskResponse current = approvalTaskStoreService.getByApprovalId(approvalId);
        if (current.status() != ApprovalStatus.PENDING) {
            return current;
        }
        String approverId = normalizeApproverId(request);
        ticketService.transitionStatus(
                current.ticketId(),
                new TransitionTicketStatusRequest(TicketStatus.ESCALATED, approverId, UserRole.APPROVER, null, safeComment(request, "审批拒绝，升级人工")));
        ApprovalTaskResponse updated = approvalTaskStoreService.markRejected(approvalId, approverId, request == null ? null : request.comment());
        conversationMessageService.appendMessageIfChanged(
                current.ticketId(),
                ConversationRole.AGENT,
                ConversationMessageType.AGENT_ESCALATION,
                "审批已拒绝，工单已升级到人工处理。");
        return updated;
    }

    private String normalizeApproverId(ApprovalDecisionRequest request) {
        if (request == null || request.approverId() == null || request.approverId().trim().isEmpty()) {
            return "APPROVER";
        }
        return request.approverId().trim();
    }

    private String safeComment(ApprovalDecisionRequest request, String defaultComment) {
        if (request == null || request.comment() == null || request.comment().trim().isEmpty()) {
            return defaultComment;
        }
        return request.comment().trim();
    }
}
