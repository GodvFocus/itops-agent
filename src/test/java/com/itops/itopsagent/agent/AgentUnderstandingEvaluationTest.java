package com.itops.itopsagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.service.agent.AgentContextSnapshot;
import com.itops.itopsagent.service.agent.AgentStructuredOutputValidator;
import com.itops.itopsagent.service.agent.IntentClassificationResult;
import com.itops.itopsagent.service.agent.IntentClassifier;
import com.itops.itopsagent.service.agent.MissingSlotQuestionGenerator;
import com.itops.itopsagent.service.agent.QuestionGenerationResult;
import com.itops.itopsagent.service.agent.SlotExtractionResult;
import com.itops.itopsagent.service.agent.SlotExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class AgentUnderstandingEvaluationTest {

    private static final TypeReference<List<SampleCase>> SAMPLE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentStructuredOutputValidator validator = new AgentStructuredOutputValidator();
    private final IntentClassifier intentClassifier = new IntentClassifier(validator);
    private final SlotExtractor slotExtractor = new SlotExtractor(validator);
    private final MissingSlotQuestionGenerator questionGenerator = new MissingSlotQuestionGenerator(validator);

    /**
     * 基于固定样本评估 Mock Agent 规则精度，
     * 先在 Phase 2 把分类、抽槽位和追问覆盖率的验收线稳住。
     */
    @Test
    void shouldMeetPhase2AcceptanceThresholds() throws Exception {
        List<SampleCase> samples = loadSamples();
        int intentMatches = 0;
        int expectedSlotCount = 0;
        int matchedSlotCount = 0;
        int expectedMissingCount = 0;
        int recalledMissingCount = 0;
        int unknownCount = 0;
        int unknownMatches = 0;

        for (SampleCase sample : samples) {
            AgentContextSnapshot snapshot = new AgentContextSnapshot(
                    Map.of(
                            "ticketId", "T-EVAL",
                            "title", sample.title(),
                            "description", sample.description(),
                            "creatorId", "U-EVAL",
                            "creatorRole", "EMPLOYEE",
                            "status", "NEW"),
                    Map.of("status", "NEW", "version", 0, "updatedAt", "2026-06-23T00:00:00Z"),
                    Map.of(),
                    List.of(),
                    List.of(Map.of(
                            "id", 1,
                            "role", "USER",
                            "content", sample.description(),
                            "messageType", "TICKET_DESCRIPTION",
                            "createdAt", "2026-06-23T00:00:00Z")),
                    "",
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of(),
                    "INIT",
                    TicketIntent.UNKNOWN);

            IntentClassificationResult intentResult = intentClassifier.classify(snapshot);
            SlotExtractionResult slotResult = slotExtractor.extract(snapshot, intentResult.intent());
            QuestionGenerationResult questionResult = questionGenerator.generate(intentResult.intent(), slotResult.missingSlots());

            if (intentResult.intent() == sample.intent()) {
                intentMatches++;
            }

            if (sample.intent() == TicketIntent.UNKNOWN) {
                unknownCount++;
                if (intentResult.intent() == TicketIntent.UNKNOWN) {
                    unknownMatches++;
                }
            }

            for (Map.Entry<String, String> entry : sample.expectedSlots().entrySet()) {
                expectedSlotCount++;
                if (entry.getValue().equals(String.valueOf(slotResult.slots().get(entry.getKey())))) {
                    matchedSlotCount++;
                }
            }

            expectedMissingCount += sample.expectedMissingSlots().size();
            for (String missingSlot : sample.expectedMissingSlots()) {
                if (slotResult.missingSlots().contains(missingSlot)) {
                    recalledMissingCount++;
                }
            }

            if (sample.intent() == TicketIntent.UNKNOWN) {
                assertThat(questionResult.shouldAskUser()).isTrue();
            } else if (sample.expectedMissingSlots().isEmpty()) {
                assertThat(questionResult.shouldAskUser()).isFalse();
            } else {
                assertThat(questionResult.shouldAskUser()).isTrue();
            }
        }

        double intentAccuracy = intentMatches / (double) samples.size();
        double slotAccuracy = matchedSlotCount / (double) expectedSlotCount;
        double missingRecall = recalledMissingCount / (double) expectedMissingCount;
        double unknownAccuracy = unknownMatches / (double) unknownCount;

        assertThat(intentAccuracy).isGreaterThanOrEqualTo(0.85d);
        assertThat(slotAccuracy).isGreaterThanOrEqualTo(0.80d);
        assertThat(missingRecall).isGreaterThanOrEqualTo(0.90d);
        assertThat(unknownAccuracy).isGreaterThanOrEqualTo(0.95d);
    }

    private List<SampleCase> loadSamples() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/phase2-agent-understanding-samples.json")) {
            return objectMapper.readValue(inputStream, SAMPLE_TYPE);
        }
    }

    private record SampleCase(
            String title,
            String description,
            TicketIntent intent,
            Map<String, String> expectedSlots,
            List<String> expectedMissingSlots) {
    }
}
