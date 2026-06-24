package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.HarnessDecisionResponse;
import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.dto.UpdateAgentContextRequest;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.service.ApprovalTaskStoreService;
import com.itops.itopsagent.service.ConversationMessageService;
import com.itops.itopsagent.service.TicketAutomationService;
import com.itops.itopsagent.service.TicketContextService;
import com.itops.itopsagent.service.agent.SopPlanService;
import com.itops.itopsagent.service.harness.HarnessPlanValidationService;
import com.itops.itopsagent.service.harness.HarnessTicketStatePort;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketAutomationServiceImpl implements TicketAutomationService {

    private final TicketMapper ticketMapper;
    private final TicketContextService ticketContextService;
    private final ConversationMessageService conversationMessageService;
    private final HarnessTicketStatePort harnessTicketStatePort;
    private final HarnessPlanValidationService harnessPlanValidationService;
    private final ApprovalTaskStoreService approvalTaskStoreService;
    private final SopPlanService sopPlanService;

    @Override
    @Transactional
    public void progressAfterUnderstanding(String ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new TicketNotFoundException(ticketId);
        }
        if (shouldSkipAutomation(ticket.getStatus())) {
            return;
        }
        TicketContextResponse context = ticketContextService.getContext(ticketId);
        if (context.intent() == TicketIntent.UNKNOWN) {
            moveToManualTakeover(ticketId, ticket.getStatus(), "当前问题不在 MVP 自动处理范围内");
            return;
        }
        if (!context.missingSlots().isEmpty()) {
            moveToNeedMoreInfo(ticketId, ticket.getStatus());
            return;
        }

        moveToPlanning(ticketId, ticket.getStatus());
        Ticket refreshed = ticketMapper.selectById(ticketId);
        SopPlanService.PlanBuildResult buildResult = sopPlanService.buildPlan(refreshed, context);
        persistPlanContext(ticketId, context, buildResult);

        CandidatePlanRequest plan = buildResult.plan();
        HarnessDecisionResponse response = harnessPlanValidationService.executePlan(plan);
        if ("NEED_APPROVAL".equals(response.decision())) {
            approvalTaskStoreService.createPendingTask(ticketId, plan, response.reason(), response.approvedSteps());
            conversationMessageService.appendMessageIfChanged(
                    ticketId,
                    ConversationRole.AGENT,
                    ConversationMessageType.AGENT_SUMMARY,
                    "已生成高风险计划并创建审批任务，等待审批通过后继续执行。");
            return;
        }
        if ("REJECTED".equals(response.decision()) || "ESCALATE".equals(response.decision())) {
            conversationMessageService.appendMessageIfChanged(
                    ticketId,
                    ConversationRole.AGENT,
                    ConversationMessageType.AGENT_ESCALATION,
                    "当前计划未通过 Harness 安全裁决，工单已转人工处理。");
            return;
        }
        conversationMessageService.appendMessageIfChanged(
                ticketId,
                ConversationRole.AGENT,
                ConversationMessageType.AGENT_SUMMARY,
                "已生成候选计划并交给 Harness 执行，请等待处理结果。");
    }

    private void persistPlanContext(String ticketId, TicketContextResponse context, SopPlanService.PlanBuildResult buildResult) {
        ticketContextService.saveContext(ticketId, new UpdateAgentContextRequest(
                context.intent(),
                context.slots(),
                List.of(),
                buildResult.matchedSopIds(),
                buildResult.planSnapshot(),
                buildResult.plan().riskLevel(),
                "PLAN_READY"));
    }

    private void moveToNeedMoreInfo(String ticketId, TicketStatus currentStatus) {
        if (currentStatus == TicketStatus.NEW) {
            harnessTicketStatePort.transition(ticketId, TicketStatus.TRIAGING, "Agent 已完成首轮理解，进入分诊");
            harnessTicketStatePort.transition(ticketId, TicketStatus.NEED_MORE_INFO, "缺少关键槽位，等待用户补充信息");
            return;
        }
        if (currentStatus == TicketStatus.TRIAGING) {
            harnessTicketStatePort.transition(ticketId, TicketStatus.NEED_MORE_INFO, "缺少关键槽位，等待用户补充信息");
        }
    }

    private void moveToPlanning(String ticketId, TicketStatus currentStatus) {
        if (currentStatus == TicketStatus.NEW) {
            harnessTicketStatePort.transition(ticketId, TicketStatus.TRIAGING, "Agent 已完成首轮理解，进入分诊");
            harnessTicketStatePort.transition(ticketId, TicketStatus.PLANNING, "关键槽位已齐备，开始生成计划");
            return;
        }
        if (currentStatus == TicketStatus.NEED_MORE_INFO) {
            harnessTicketStatePort.transition(ticketId, TicketStatus.TRIAGING, "用户已补充关键信息，重新进入分诊");
            harnessTicketStatePort.transition(ticketId, TicketStatus.PLANNING, "关键信息已补齐，开始生成计划");
            return;
        }
        if (currentStatus == TicketStatus.TRIAGING) {
            harnessTicketStatePort.transition(ticketId, TicketStatus.PLANNING, "关键槽位已齐备，开始生成计划");
        }
    }

    private void moveToManualTakeover(String ticketId, TicketStatus currentStatus, String message) {
        if (currentStatus == TicketStatus.NEW) {
            harnessTicketStatePort.transition(ticketId, TicketStatus.MANUAL_TAKEOVER, message);
            return;
        }
        if (currentStatus == TicketStatus.TRIAGING || currentStatus == TicketStatus.NEED_MORE_INFO || currentStatus == TicketStatus.PLANNING) {
            harnessTicketStatePort.transition(ticketId, TicketStatus.MANUAL_TAKEOVER, message);
        }
    }

    private boolean shouldSkipAutomation(TicketStatus status) {
        return status == TicketStatus.WAITING_APPROVAL
                || status == TicketStatus.EXECUTING
                || status == TicketStatus.WAITING_USER_CONFIRM
                || status == TicketStatus.RESOLVED
                || status == TicketStatus.CLOSED
                || status == TicketStatus.MANUAL_TAKEOVER
                || status == TicketStatus.ESCALATED;
    }
}
