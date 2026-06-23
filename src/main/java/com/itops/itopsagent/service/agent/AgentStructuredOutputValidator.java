package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.utils.exception.TicketValidationException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentStructuredOutputValidator {

    /**
     * Phase 2 先用轻量字段校验锁住 Mock LLM 输出契约，
     * 这样后续切换真实模型时不会把脏 JSON 直接写进业务事实表。
     */
    public IntentClassificationResult validateIntentPayload(Map<String, Object> payload) {
        Object intentValue = payload.get("intent");
        Object confidenceValue = payload.get("confidence");
        Object reasoningValue = payload.get("reasoning");
        if (!(intentValue instanceof String rawIntent) || !(confidenceValue instanceof Number confidence) || !(reasoningValue instanceof String reasoning)) {
            throw new TicketValidationException("Intent payload schema validation failed");
        }
        return new IntentClassificationResult(TicketIntent.valueOf(rawIntent), confidence.doubleValue(), reasoning);
    }

    public SlotExtractionResult validateSlotPayload(Map<String, Object> payload) {
        Object slotsValue = payload.get("slots");
        Object missingValue = payload.get("missingSlots");
        Object reasoningValue = payload.get("reasoning");
        if (!(slotsValue instanceof Map<?, ?> rawSlots) || !(missingValue instanceof List<?> rawMissing) || !(reasoningValue instanceof String reasoning)) {
            throw new TicketValidationException("Slot payload schema validation failed");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> slots = (Map<String, Object>) rawSlots;
        List<String> missingSlots = rawMissing.stream().map(String::valueOf).toList();
        return new SlotExtractionResult(slots, missingSlots, reasoning);
    }

    public QuestionGenerationResult validateQuestionPayload(Map<String, Object> payload) {
        Object shouldAskValue = payload.get("shouldAskUser");
        Object questionValue = payload.get("question");
        Object nextStepValue = payload.get("nextStep");
        if (!(shouldAskValue instanceof Boolean shouldAskUser) || !(questionValue instanceof String question) || !(nextStepValue instanceof String nextStep)) {
            throw new TicketValidationException("Question payload schema validation failed");
        }
        return new QuestionGenerationResult(shouldAskUser, question, nextStep);
    }
}
