package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.PlanStepRequest;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.ToolActionType;
import com.itops.itopsagent.utils.exception.TicketValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HarnessRiskEvaluator {

    private static final List<TicketStatus> ALLOWED_STATUSES = List.of(
            TicketStatus.PLANNING,
            TicketStatus.PLAN_VALIDATING,
            TicketStatus.WAITING_APPROVAL,
            TicketStatus.EXECUTING);
    private final ToolRegistryService toolRegistryService;

    public HarnessRiskEvaluator(ToolRegistryService toolRegistryService) {
        this.toolRegistryService = toolRegistryService;
    }

    public List<PlanStepAssessment> evaluate(CandidatePlanRequest request, TicketStatus currentStatus) {
        validatePlanShape(request);
        if (currentStatus == null) {
            return rejectAll(request.steps(), "工单不存在，Harness 无法执行。");
        }
        if (!ALLOWED_STATUSES.contains(currentStatus)) {
            return rejectAll(request.steps(), "当前工单状态不允许执行 Candidate Plan。");
        }
        List<PlanStepAssessment> assessments = new ArrayList<>();
        for (PlanStepRequest step : request.steps()) {
            assessments.add(evaluateStep(request, step));
        }
        return assessments;
    }

    private PlanStepAssessment evaluateStep(CandidatePlanRequest request, PlanStepRequest step) {
        if (step == null || step.stepNo() == null || isBlank(step.tool()) || isBlank(step.action()) || step.params() == null || isBlank(step.reason())) {
            return rejected(step, null, "Step 缺少必填字段");
        }
        ToolActionType actionType = parseActionType(step.actionType());
        if (actionType == null) {
            return rejected(step, null, "Step actionType 不在合同范围内");
        }
        ToolRegistryEntry entry = toolRegistryService.find(step.tool(), step.action()).orElse(null);
        if (entry == null) {
            return rejected(step, actionType, "Step 使用了未注册工具");
        }
        if (entry.actionType() != actionType) {
            return rejected(step, actionType, "Step actionType 与 Tool Registry 不一致");
        }
        for (String requiredParam : entry.requiredParams()) {
            Object value = step.params().get(requiredParam);
            if (value == null || (value instanceof String text && text.isBlank())) {
                return rejected(step, actionType, "Step 缺少必要参数: " + requiredParam);
            }
        }
        if (entry.actionType() == ToolActionType.WRITE && toolRegistryService.buildIdemKey(entry, request.ticketId(), request.planId(), step.stepNo(), step.params()) == null) {
            return rejected(step, actionType, "WRITE 操作无法生成 idem_key");
        }

        RiskLevel effectiveRisk = resolveEffectiveRisk(step, entry);
        if (entry.tool().equals("AccountTool") && entry.action().equals("unlockAccount") && isDisabledAccount(step.params())) {
            return rejected(step, actionType, "禁用账号禁止自动解锁");
        }
        if (entry.tool().equals("PermissionTool") && entry.action().equals("grantPermission") && isDeletePermission(step.params())) {
            return rejected(step, actionType, "删除或撤销权限属于危险操作，Harness 已拦截");
        }

        boolean needsApproval = entry.approvalRequirement() == ApprovalRequirement.REQUIRED
                || (entry.approvalRequirement() == ApprovalRequirement.CONDITIONAL && isConditionalApproval(step.params(), effectiveRisk));
        String idemKey = toolRegistryService.buildIdemKey(entry, request.ticketId(), request.planId(), step.stepNo(), step.params());

        if (needsApproval) {
            return new PlanStepAssessment(
                    step.stepNo(),
                    step.tool(),
                    step.action(),
                    actionType,
                    step.params(),
                    effectiveRisk,
                    StepDecision.NEED_APPROVAL,
                    true,
                    idemKey,
                    "步骤触发企业风险策略，需要审批后才能继续。");
        }
        return new PlanStepAssessment(
                step.stepNo(),
                step.tool(),
                step.action(),
                actionType,
                step.params(),
                effectiveRisk,
                StepDecision.APPROVED,
                false,
                idemKey,
                "步骤通过 Harness 风险评估，可进入自动执行。");
    }

    private List<PlanStepAssessment> rejectAll(List<PlanStepRequest> steps, String reason) {
        List<PlanStepAssessment> rejected = new ArrayList<>();
        for (PlanStepRequest step : steps) {
            rejected.add(rejected(step, null, reason));
        }
        return rejected;
    }

    private RiskLevel resolveEffectiveRisk(PlanStepRequest step, ToolRegistryEntry entry) {
        if ("PermissionTool".equals(step.tool()) && "grantPermission".equals(step.action())) {
            if (isDeletePermission(step.params())) {
                return RiskLevel.FORBIDDEN;
            }
            if (isProductionAdmin(step.params())) {
                return RiskLevel.HIGH;
            }
            if (isAdminPermission(step.params())) {
                return RiskLevel.HIGH;
            }
        }
        if ("AccountTool".equals(step.tool()) && "unlockAccount".equals(step.action()) && isDisabledAccount(step.params())) {
            return RiskLevel.FORBIDDEN;
        }
        return step.riskLevel() == null ? entry.defaultRisk() : step.riskLevel();
    }

    private boolean isConditionalApproval(Map<String, Object> params, RiskLevel effectiveRisk) {
        return effectiveRisk == RiskLevel.HIGH || isProductionAdmin(params) || isAdminPermission(params);
    }

    private boolean isProductionAdmin(Map<String, Object> params) {
        return isProductionSystem(params) && isAdminPermission(params);
    }

    private boolean isProductionSystem(Map<String, Object> params) {
        String targetSystem = normalize(params.get("targetSystem"));
        return targetSystem.contains("PROD") || targetSystem.contains("PRODUCTION");
    }

    private boolean isAdminPermission(Map<String, Object> params) {
        String permissionLevel = normalize(params.get("permissionLevel"));
        return permissionLevel.equals("ADMIN")
                || permissionLevel.equals("ROOT")
                || permissionLevel.equals("OWNER")
                || permissionLevel.equals("SUPER_ADMIN");
    }

    private boolean isDeletePermission(Map<String, Object> params) {
        String permissionLevel = normalize(params.get("permissionLevel"));
        String requestedAction = normalize(params.get("requestedAction"));
        return permissionLevel.equals("DELETE")
                || permissionLevel.equals("REMOVE")
                || permissionLevel.equals("REVOKE")
                || requestedAction.equals("DELETE")
                || requestedAction.equals("REMOVE")
                || requestedAction.equals("REVOKE");
    }

    private boolean isDisabledAccount(Map<String, Object> params) {
        return normalize(params.get("accountStatus")).equals("DISABLED");
    }

    private ToolActionType parseActionType(String actionType) {
        try {
            return ToolActionType.valueOf(actionType);
        } catch (Exception exception) {
            return null;
        }
    }

    private PlanStepAssessment rejected(PlanStepRequest step, ToolActionType actionType, String reason) {
        return new PlanStepAssessment(
                step == null ? null : step.stepNo(),
                step == null ? null : step.tool(),
                step == null ? null : step.action(),
                actionType == null ? ToolActionType.READ : actionType,
                step == null ? Map.of() : step.params(),
                RiskLevel.FORBIDDEN,
                StepDecision.REJECTED,
                false,
                null,
                reason);
    }

    private void validatePlanShape(CandidatePlanRequest request) {
        if (request == null) {
            throw new TicketValidationException("Candidate Plan request cannot be null");
        }
        if (isBlank(request.planId()) || isBlank(request.ticketId()) || request.intent() == null || request.riskLevel() == null) {
            throw new TicketValidationException("Candidate Plan 缺少必填字段");
        }
        if (request.steps() == null || request.steps().isEmpty()) {
            throw new TicketValidationException("Candidate Plan steps cannot be empty");
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
