package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.HarnessDecisionResponse;
import com.itops.itopsagent.dto.PlanStepRequest;
import com.itops.itopsagent.utils.exception.TicketValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class HarnessPlanValidationService {

    private static final Path TOOL_REGISTRY_PATH = Path.of("docs", "itops_agent_codex_task_pack", "contracts", "tool_registry.yaml");
    private static final List<String> ALLOWED_ACTION_TYPES = List.of("READ", "WRITE", "APPROVAL_REQUIRED", "FORBIDDEN");

    public HarnessDecisionResponse validatePlan(CandidatePlanRequest request) {
        validatePlanShape(request);

        Map<String, ToolRegistryEntry> registry = loadToolRegistry();
        List<Map<String, Object>> rejectedSteps = new ArrayList<>();
        List<Map<String, Object>> approvedSteps = new ArrayList<>();
        boolean hasApprovalStep = false;

        for (PlanStepRequest step : request.steps()) {
            Map<String, Object> validation = validateStep(step, registry);
            if (Boolean.TRUE.equals(validation.get("rejected"))) {
                rejectedSteps.add(validation);
                continue;
            }
            approvedSteps.add(validation);
            if (Boolean.TRUE.equals(step.requiredApproval())) {
                hasApprovalStep = true;
            }
        }

        if (!rejectedSteps.isEmpty()) {
            return new HarnessDecisionResponse(
                    request.ticketId(),
                    request.planId(),
                    "REJECTED",
                    "NONE",
                    "Plan 未通过 Harness 基础校验。",
                    null,
                    rejectedSteps,
                    approvedSteps);
        }
        if (request.intent() != null && request.intent().name().equals("UNKNOWN")) {
            return new HarnessDecisionResponse(
                    request.ticketId(),
                    request.planId(),
                    "ESCALATE",
                    "NONE",
                    "UNKNOWN 工单不进入自动执行，建议升级人工。",
                    null,
                    List.of(),
                    approvedSteps);
        }
        if (hasApprovalStep) {
            return new HarnessDecisionResponse(
                    request.ticketId(),
                    request.planId(),
                    "NEED_APPROVAL",
                    "PAUSE",
                    "Plan 包含高风险或审批步骤，需进入审批门禁。",
                    "MANUAL_APPROVAL",
                    List.of(),
                    approvedSteps);
        }
        return new HarnessDecisionResponse(
                request.ticketId(),
                request.planId(),
                "APPROVED",
                "NONE",
                "Plan 已通过 Phase 3 Harness stub 基础校验。",
                null,
                List.of(),
                approvedSteps);
    }

    private void validatePlanShape(CandidatePlanRequest request) {
        if (request == null) {
            throw new TicketValidationException("Candidate Plan request cannot be null");
        }
        if (isBlank(request.planId()) || isBlank(request.ticketId()) || request.intent() == null || request.riskLevel() == null) {
            throw new TicketValidationException("Candidate Plan 缺少必填字段");
        }
        if (request.steps() == null) {
            throw new TicketValidationException("Candidate Plan steps cannot be null");
        }
    }

    private Map<String, Object> validateStep(PlanStepRequest step, Map<String, ToolRegistryEntry> registry) {
        if (step == null || step.stepNo() == null || isBlank(step.tool()) || isBlank(step.action()) || step.params() == null
                || step.riskLevel() == null || step.requiredApproval() == null || isBlank(step.reason())) {
            return rejectedStep(step, "Step 缺少必填字段");
        }
        if (!ALLOWED_ACTION_TYPES.contains(step.actionType())) {
            return rejectedStep(step, "Step actionType 不在合同范围内");
        }

        ToolRegistryEntry registryEntry = registry.get(step.tool() + "." + step.action());
        if (registryEntry == null) {
            return rejectedStep(step, "Step 使用了未注册工具");
        }
        if (!isActionTypeCompatible(step.actionType(), registryEntry.actionType())) {
            return rejectedStep(step, "Step actionType 与 Tool Registry 不一致");
        }
        for (String requiredParam : registryEntry.requiredParams()) {
            Object value = step.params().get(requiredParam);
            if (value == null || (value instanceof String text && text.isBlank())) {
                return rejectedStep(step, "Step 缺少必要参数: " + requiredParam);
            }
        }
        if (registryEntry.requiresApproval() && !Boolean.TRUE.equals(step.requiredApproval())) {
            return rejectedStep(step, "高风险步骤缺少 requiredApproval 标记");
        }
        if (registryEntry.isConditionalApproval()
                && step.riskLevel().name().equals("HIGH")
                && !Boolean.TRUE.equals(step.requiredApproval())) {
            return rejectedStep(step, "条件审批步骤在高风险场景下缺少 requiredApproval");
        }

        Map<String, Object> approved = new LinkedHashMap<>();
        approved.put("stepNo", step.stepNo());
        approved.put("tool", step.tool());
        approved.put("action", step.action());
        approved.put("requiredApproval", step.requiredApproval());
        return approved;
    }

    private boolean isActionTypeCompatible(String stepActionType, String registryActionType) {
        if (Objects.equals(stepActionType, registryActionType)) {
            return true;
        }
        return registryActionType.equals("WRITE") && stepActionType.equals("APPROVAL_REQUIRED");
    }

    private Map<String, ToolRegistryEntry> loadToolRegistry() {
        try {
            List<String> lines = Files.readAllLines(TOOL_REGISTRY_PATH);
            List<ToolRegistryEntry> entries = new ArrayList<>();
            Map<String, Object> current = new LinkedHashMap<>();
            for (String line : lines) {
                String stripped = line.trim();
                if (stripped.isEmpty() || stripped.startsWith("#") || stripped.equals("tools:")) {
                    continue;
                }
                if (stripped.startsWith("- ")) {
                    if (!current.isEmpty()) {
                        entries.add(toEntry(current));
                    }
                    current = new LinkedHashMap<>();
                    stripped = stripped.substring(2).trim();
                }
                int separator = stripped.indexOf(':');
                if (separator < 0) {
                    continue;
                }
                String key = stripped.substring(0, separator).trim();
                String value = stripped.substring(separator + 1).trim();
                current.put(key, parseScalar(value));
            }
            if (!current.isEmpty()) {
                entries.add(toEntry(current));
            }

            Map<String, ToolRegistryEntry> registry = new LinkedHashMap<>();
            for (ToolRegistryEntry entry : entries) {
                registry.put(entry.tool() + "." + entry.action(), entry);
            }
            return registry;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read tool registry contract", exception);
        }
    }

    private ToolRegistryEntry toEntry(Map<String, Object> raw) {
        @SuppressWarnings("unchecked")
        List<String> requiredParams = (List<String>) raw.getOrDefault("requiredParams", List.of());
        return new ToolRegistryEntry(
                String.valueOf(raw.get("tool")),
                String.valueOf(raw.get("action")),
                String.valueOf(raw.get("actionType")),
                String.valueOf(raw.get("defaultRisk")),
                requiredParams,
                String.valueOf(raw.get("approvalRequired")));
    }

    private Object parseScalar(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            String inner = value.substring(1, value.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }
            return List.of(inner.split(",")).stream().map(item -> item.trim().replace("\"", "")).toList();
        }
        return value;
    }

    private Map<String, Object> rejectedStep(PlanStepRequest step, String reason) {
        Map<String, Object> rejected = new LinkedHashMap<>();
        rejected.put("rejected", true);
        rejected.put("stepNo", step == null ? null : step.stepNo());
        rejected.put("tool", step == null ? null : step.tool());
        rejected.put("action", step == null ? null : step.action());
        rejected.put("reason", reason);
        return rejected;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ToolRegistryEntry(
            String tool,
            String action,
            String actionType,
            String defaultRisk,
            List<String> requiredParams,
            String approvalRequired) {

        private boolean requiresApproval() {
            return "true".equalsIgnoreCase(approvalRequired);
        }

        private boolean isConditionalApproval() {
            return "conditional".equalsIgnoreCase(approvalRequired);
        }
    }
}
