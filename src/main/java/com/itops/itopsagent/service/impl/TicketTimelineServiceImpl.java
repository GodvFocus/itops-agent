package com.itops.itopsagent.service.impl;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.dto.ApprovalTaskResponse;
import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.dto.TicketTimelineResponse;
import com.itops.itopsagent.dto.TimelineEventResponse;
import com.itops.itopsagent.dto.ToolCallLogResponse;
import com.itops.itopsagent.entity.ToolCallLog;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.service.AgentStepLogService;
import com.itops.itopsagent.service.ApprovalTaskStoreService;
import com.itops.itopsagent.service.TicketContextService;
import com.itops.itopsagent.service.TicketTimelineService;
import com.itops.itopsagent.service.harness.HarnessToolCallLogService;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketTimelineServiceImpl implements TicketTimelineService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final TicketMapper ticketMapper;
    private final TicketContextService ticketContextService;
    private final AgentStepLogService agentStepLogService;
    private final HarnessToolCallLogService harnessToolCallLogService;
    private final ApprovalTaskStoreService approvalTaskStoreService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public TicketTimelineResponse getTimeline(String ticketId) {
        if (ticketMapper.selectById(ticketId) == null) {
            throw new TicketNotFoundException(ticketId);
        }
        TicketContextResponse context = ticketContextService.getContext(ticketId);
        List<ApprovalTaskResponse> approvalTasks = approvalTaskStoreService.listByTicketId(ticketId);
        List<ToolCallLogResponse> toolCalls = harnessToolCallLogService.findByTicketId(ticketId).stream().map(this::toToolCallResponse).toList();
        List<TimelineEventResponse> timeline = buildTimeline(ticketId, approvalTasks, toolCalls);
        return new TicketTimelineResponse(
                context.currentPlan(),
                context.matchedSopIds(),
                approvalTasks,
                toolCalls,
                timeline,
                buildSummary(approvalTasks, toolCalls));
    }

    private List<TimelineEventResponse> buildTimeline(String ticketId, List<ApprovalTaskResponse> approvalTasks, List<ToolCallLogResponse> toolCalls) {
        List<TimelineEventResponse> events = new ArrayList<>();
        agentStepLogService.listLogs(ticketId).forEach(log -> events.add(new TimelineEventResponse(
                "AGENT",
                "AGENT_STEP",
                log.nodeName(),
                stringify(log.output()),
                log.status().name(),
                log.createdAt())));
        approvalTasks.forEach(task -> {
            events.add(new TimelineEventResponse(
                    "APPROVAL",
                    "APPROVAL_TASK",
                    "审批任务 " + task.approvalId(),
                    task.requestedReason(),
                    task.status().name(),
                    task.createdAt()));
            if (task.decidedAt() != null) {
                events.add(new TimelineEventResponse(
                        "APPROVAL",
                        "APPROVAL_DECISION",
                        task.status() == com.itops.itopsagent.entity.enums.ApprovalStatus.APPROVED ? "审批通过" : "审批拒绝",
                        safeText(task.approverComment(), "审批人未补充备注"),
                        task.status().name(),
                        task.decidedAt()));
            }
        });
        toolCalls.forEach(call -> events.add(new TimelineEventResponse(
                "TOOL",
                "TOOL_CALL",
                call.toolName() + "." + call.actionName(),
                "step " + call.stepNo() + " | " + safeText(call.errorMessage(), call.decision()),
                call.status().name(),
                call.createdAt())));
        events.sort(Comparator.comparing(TimelineEventResponse::createdAt));
        return events;
    }

    private String buildSummary(List<ApprovalTaskResponse> approvalTasks, List<ToolCallLogResponse> toolCalls) {
        ApprovalTaskResponse pendingApproval = approvalTasks.stream()
                .filter(task -> task.status() == com.itops.itopsagent.entity.enums.ApprovalStatus.PENDING)
                .findFirst()
                .orElse(null);
        if (pendingApproval != null) {
            return "Harness 已拦截高风险动作并创建审批任务，等待审批后恢复执行。";
        }
        ApprovalTaskResponse rejectedApproval = approvalTasks.stream()
                .filter(task -> task.status() == com.itops.itopsagent.entity.enums.ApprovalStatus.REJECTED)
                .findFirst()
                .orElse(null);
        if (rejectedApproval != null) {
            return "审批已拒绝，工单已升级到人工处理。";
        }
        if (hasSuccessfulAction(toolCalls, "AccountTool", "unlockAccount")) {
            return "已查询账号状态并执行自动解锁，等待用户确认登录是否恢复。";
        }
        if (hasSuccessfulAction(toolCalls, "PermissionTool", "grantPermission")) {
            return "审批通过后已执行权限授予，等待用户确认排障是否完成。";
        }
        ToolCallLogResponse mfaStatus = latestSuccessfulAction(toolCalls, "MfaTool", "queryMfaStatus");
        if (mfaStatus != null) {
            Object bindingStatus = mfaStatus.response().get("bindingStatus");
            if (bindingStatus != null && !"BOUND".equals(String.valueOf(bindingStatus))) {
                return "已确认 VPN 异常与 MFA 状态异常相关，建议按摘要继续处理或转人工复核。";
            }
            return "已完成 VPN 与 MFA 基础排查，等待用户确认网络恢复情况。";
        }
        if (toolCalls.isEmpty()) {
            return "当前还没有工具执行记录，请先完成理解、计划或审批步骤。";
        }
        return "处理链路已产生工具证据，请结合时间线和对话记录继续跟进。";
    }

    private ToolCallLogResponse latestSuccessfulAction(List<ToolCallLogResponse> toolCalls, String tool, String action) {
        return toolCalls.stream()
                .filter(call -> call.toolName().equals(tool) && call.actionName().equals(action) && "SUCCESS".equals(call.status().name()))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private boolean hasSuccessfulAction(List<ToolCallLogResponse> toolCalls, String tool, String action) {
        return latestSuccessfulAction(toolCalls, tool, action) != null;
    }

    private ToolCallLogResponse toToolCallResponse(ToolCallLog log) {
        return new ToolCallLogResponse(
                log.getId(),
                log.getPlanId(),
                log.getStepNo(),
                log.getToolName(),
                log.getActionName(),
                log.getActionType(),
                log.getIdemKey(),
                log.getStatus(),
                log.getDecision(),
                deserialize(log.getRequestJson()),
                deserialize(log.getResponseJson()),
                log.getErrorMessage(),
                log.getAttemptNo(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }

    private Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize tool call payload", exception);
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return String.valueOf(value);
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
