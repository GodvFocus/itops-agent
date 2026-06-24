package com.itops.itopsagent.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.PlanStepRequest;
import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.ToolActionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SopPlanServiceImpl implements SopPlanService {

    private final ObjectMapper objectMapper;

    @Override
    public PlanBuildResult buildPlan(Ticket ticket, TicketContextResponse context) {
        Map<String, Object> slots = context.slots();
        return switch (context.intent()) {
            case ACCOUNT_LOGIN_ISSUE -> buildAccountPlan(ticket, slots);
            case VPN_CONNECTION_ISSUE -> buildVpnPlan(ticket, slots);
            case PERMISSION_REQUEST -> buildPermissionPlan(ticket, slots);
            case UNKNOWN -> throw new IllegalArgumentException("UNKNOWN intent cannot build plan");
        };
    }

    private PlanBuildResult buildAccountPlan(Ticket ticket, Map<String, Object> slots) {
        String errorMessage = String.valueOf(slots.getOrDefault("errorMessage", ""));
        boolean locked = errorMessage.contains("锁定") || errorMessage.toUpperCase(Locale.ROOT).contains("LOCKED");
        String sopId = locked ? "SOP-ACC-LOCKED-001" : "SOP-ACC-LOGIN-ABNORMAL-001";
        List<PlanStepRequest> steps = locked
                ? List.of(
                        step(1, "AccountTool", "queryAccountStatus", ToolActionType.READ, Map.of("employeeId", slots.get("employeeId")), RiskLevel.LOW, false, "先核实账号是否真的处于锁定状态。"),
                        step(2, "AccountTool", "unlockAccount", ToolActionType.WRITE, Map.of("employeeId", slots.get("employeeId")), RiskLevel.MEDIUM, false, "低风险解锁动作可直接交给 Harness 执行。"),
                        step(3, "NotificationTool", "sendNotification", ToolActionType.WRITE, notificationParams(ticket, slots, "账号已触发自动解锁处理，请重新尝试登录。"), RiskLevel.LOW, false, "自动处理后要把结果同步给用户。"))
                : List.of(
                        step(1, "AccountTool", "queryAccountStatus", ToolActionType.READ, Map.of("employeeId", slots.get("employeeId")), RiskLevel.LOW, false, "需要先区分是锁定还是普通登录异常。"),
                        step(2, "NotificationTool", "sendNotification", ToolActionType.WRITE, notificationParams(ticket, slots, "当前未发现可自动修复动作，建议人工继续排查认证链路。"), RiskLevel.LOW, false, "没有密码重置工具时，至少要把排查结论反馈给用户。"));
        return buildResult(ticket, TicketIntent.ACCOUNT_LOGIN_ISSUE, locked ? RiskLevel.LOW : RiskLevel.MEDIUM, sopId, List.of(sopId), steps);
    }

    private PlanBuildResult buildVpnPlan(Ticket ticket, Map<String, Object> slots) {
        boolean changedDevice = Boolean.TRUE.equals(slots.get("mfaRecentlyChanged"));
        List<String> matchedSops = changedDevice
                ? List.of("SOP-VPN-AUTH-FAIL-001", "SOP-MFA-DEVICE-CHANGE-001")
                : List.of("SOP-VPN-AUTH-FAIL-001");
        Map<String, Object> mfaParams = new LinkedHashMap<>();
        mfaParams.put("employeeId", slots.get("employeeId"));
        if (changedDevice) {
            // 固定演示数据把“刚换手机”映射成 MFA 异常，便于稳定复现 Demo。
            mfaParams.put("bindingStatus", "REBIND_REQUIRED");
        }
        List<PlanStepRequest> steps = List.of(
                step(1, "VpnTool", "queryVpnLoginFailure", ToolActionType.READ, Map.of("employeeId", slots.get("employeeId")), RiskLevel.LOW, false, "VPN 认证失败先查失败记录，缩小问题范围。"),
                step(2, "MfaTool", "queryMfaStatus", ToolActionType.READ, mfaParams, RiskLevel.LOW, false, "MFA 与 VPN 登录链路强耦合，需要一起核验。"),
                step(3, "NotificationTool", "sendNotification", ToolActionType.WRITE, notificationParams(ticket, slots, changedDevice
                        ? "已确认 VPN 失败与 MFA 换绑异常相关，请按指引完成后续处理。"
                        : "已完成 VPN 基础排查，请根据通知继续确认。"), RiskLevel.LOW, false, "排查完成后要及时同步处理建议。"));
        return buildResult(ticket, TicketIntent.VPN_CONNECTION_ISSUE, RiskLevel.LOW, "SOP-VPN-AUTH-FAIL-001", matchedSops, steps);
    }

    private PlanBuildResult buildPermissionPlan(Ticket ticket, Map<String, Object> slots) {
        String targetSystem = String.valueOf(slots.getOrDefault("targetSystem", ""));
        String permissionLevel = String.valueOf(slots.getOrDefault("permissionLevel", ""));
        boolean highRisk = permissionLevel.equalsIgnoreCase("ADMIN")
                || targetSystem.toLowerCase(Locale.ROOT).contains("production")
                || targetSystem.contains("生产");
        String sopId = highRisk ? "SOP-PERM-HIGH-RISK-APPROVAL-001" : resolveStandardPermissionSop(targetSystem);
        List<PlanStepRequest> steps = highRisk
                ? List.of(
                        step(1, "PermissionTool", "queryPermission", ToolActionType.READ, permissionParams(slots, false), RiskLevel.LOW, false, "高风险授权前要先确认权限基线。"),
                        step(2, "NotificationTool", "sendNotification", ToolActionType.WRITE, notificationParams(ticket, slots, "已生成高风险权限申请，等待审批后继续执行。"), RiskLevel.LOW, false, "先通知申请人与审批链路，避免黑盒等待。"),
                        step(3, "PermissionTool", "grantPermission", ToolActionType.WRITE, permissionParams(slots, true), RiskLevel.HIGH, true, "高风险权限授予必须带审批标记。"))
                : List.of(
                        step(1, "PermissionTool", "queryPermission", ToolActionType.READ, permissionParams(slots, false), RiskLevel.LOW, false, "授予前先核实现有权限，避免重复开通。"),
                        step(2, "PermissionTool", "grantPermission", ToolActionType.WRITE, permissionParams(slots, true), RiskLevel.MEDIUM, false, "标准权限申请可由 Harness 自动放行。"),
                        step(3, "NotificationTool", "sendNotification", ToolActionType.WRITE, notificationParams(ticket, slots, "标准权限申请已进入自动处理流程。"), RiskLevel.LOW, false, "授予结果要同步给申请人。"));
        return buildResult(ticket, TicketIntent.PERMISSION_REQUEST, highRisk ? RiskLevel.HIGH : RiskLevel.MEDIUM, sopId, List.of(sopId), steps);
    }

    private String resolveStandardPermissionSop(String targetSystem) {
        String normalized = targetSystem == null ? "" : targetSystem.toUpperCase(Locale.ROOT);
        if (normalized.contains("GITLAB")) {
            return "SOP-PERM-GITLAB-STANDARD-001";
        }
        return "SOP-PERM-JIRA-STANDARD-001";
    }

    private PlanBuildResult buildResult(
            Ticket ticket,
            TicketIntent intent,
            RiskLevel riskLevel,
            String selectedSopId,
            List<String> matchedSopIds,
            List<PlanStepRequest> steps) {
        CandidatePlanRequest plan = new CandidatePlanRequest(
                buildPlanId(ticket.getTicketId(), selectedSopId),
                ticket.getTicketId(),
                intent,
                riskLevel,
                "基于命中 SOP 生成候选处理计划，交由 Java Harness 做最终执行裁决。",
                steps);
        Map<String, Object> snapshot = objectMapper.convertValue(plan, new TypeReference<Map<String, Object>>() {
        });
        snapshot.put("selectedSopId", selectedSopId);
        snapshot.put("matchedSopIds", matchedSopIds);
        return new PlanBuildResult(selectedSopId, matchedSopIds, plan, snapshot);
    }

    private Map<String, Object> notificationParams(Ticket ticket, Map<String, Object> slots, String message) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("recipientId", slots.getOrDefault("employeeId", ticket.getCreatorId()));
        params.put("message", "工单 " + ticket.getTicketId() + "：" + message);
        return params;
    }

    private Map<String, Object> permissionParams(Map<String, Object> slots, boolean includePermissionLevel) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("employeeId", slots.get("employeeId"));
        params.put("targetSystem", slots.get("targetSystem"));
        if (includePermissionLevel) {
            params.put("permissionLevel", slots.get("permissionLevel"));
        }
        return params;
    }

    private PlanStepRequest step(
            int stepNo,
            String tool,
            String action,
            ToolActionType actionType,
            Map<String, Object> params,
            RiskLevel riskLevel,
            boolean requiredApproval,
            String reason) {
        return new PlanStepRequest(stepNo, tool, action, actionType.name(), params, riskLevel, requiredApproval, reason);
    }

    private String buildPlanId(String ticketId, String sopId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] raw = digest.digest((ticketId + ":" + sopId).getBytes(StandardCharsets.UTF_8));
            return "plan-" + HexFormat.of().formatHex(raw).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to build plan id", exception);
        }
    }
}
