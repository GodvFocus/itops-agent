package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.entity.enums.TicketIntent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MissingSlotQuestionGenerator {

    private static final Map<String, String> SLOT_LABELS = Map.of(
            "employeeId", "员工编号（employeeId）",
            "targetSystem", "目标系统（targetSystem）",
            "deviceType", "设备类型（deviceType）",
            "errorMessage", "错误提示（errorMessage）",
            "permissionLevel", "权限级别（permissionLevel）",
            "reason", "申请原因（reason）",
            "duration", "权限时长（duration）");

    private final AgentStructuredOutputValidator validator;

    public MissingSlotQuestionGenerator(AgentStructuredOutputValidator validator) {
        this.validator = validator;
    }

    public QuestionGenerationResult generate(TicketIntent intent, List<String> missingSlots) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (intent == TicketIntent.UNKNOWN) {
            payload.put("shouldAskUser", true);
            payload.put("question", "这个问题当前不在 MVP 支持范围内，我已建议转人工处理。若要继续排查，请补充更具体的 IT 场景说明。");
            payload.put("nextStep", "ESCALATE_TO_HUMAN");
            return validator.validateQuestionPayload(payload);
        }
        if (missingSlots.isEmpty()) {
            payload.put("shouldAskUser", false);
            payload.put("question", "");
            payload.put("nextStep", "UNDERSTANDING_READY");
            return validator.validateQuestionPayload(payload);
        }
        String labels = missingSlots.stream().map(slot -> SLOT_LABELS.getOrDefault(slot, slot)).reduce((left, right) -> left + "、" + right).orElse("");
        payload.put("shouldAskUser", true);
        payload.put("question", "为了继续处理这个工单，请补充：" + labels + "。");
        payload.put("nextStep", "ASK_USER_FOR_MISSING_SLOTS");
        return validator.validateQuestionPayload(payload);
    }
}
