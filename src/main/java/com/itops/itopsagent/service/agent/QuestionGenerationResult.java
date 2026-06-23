package com.itops.itopsagent.service.agent;

public record QuestionGenerationResult(
        boolean shouldAskUser,
        String question,
        String nextStep) {
}
