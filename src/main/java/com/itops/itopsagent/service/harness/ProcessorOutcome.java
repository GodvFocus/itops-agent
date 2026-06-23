package com.itops.itopsagent.service.harness;

public record ProcessorOutcome(
        ProcessorOutcomeType type,
        ToolExecutionTask nextTask) {

    static ProcessorOutcome completed() {
        return new ProcessorOutcome(ProcessorOutcomeType.COMPLETED, null);
    }

    static ProcessorOutcome retry(ToolExecutionTask nextTask) {
        return new ProcessorOutcome(ProcessorOutcomeType.RETRY, nextTask);
    }

    static ProcessorOutcome requeue(ToolExecutionTask nextTask) {
        return new ProcessorOutcome(ProcessorOutcomeType.REQUEUE, nextTask);
    }

    static ProcessorOutcome deadLetter(ToolExecutionTask failedTask) {
        return new ProcessorOutcome(ProcessorOutcomeType.DEAD_LETTER, failedTask);
    }
}
