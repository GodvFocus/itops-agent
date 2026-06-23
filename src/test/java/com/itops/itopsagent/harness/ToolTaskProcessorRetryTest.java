package com.itops.itopsagent.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.ToolActionType;
import com.itops.itopsagent.entity.enums.ToolCallStatus;
import com.itops.itopsagent.harness.support.HarnessTestSupport;
import com.itops.itopsagent.service.harness.ToolExecutionTask;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolTaskProcessorRetryTest {

    @Test
    void shouldRetryAndEventuallySucceed() {
        try (HarnessTestSupport support = new HarnessTestSupport()) {
            support.ticketPort.put("T-RETRY-001", TicketStatus.EXECUTING);
            support.planExecutionTracker.register("plan-retry-001", java.util.Set.of(1));
            support.queue.publish(new ToolExecutionTask(
                    "T-RETRY-001",
                    "plan-retry-001",
                    1,
                    "NotificationTool",
                    "sendNotification",
                    ToolActionType.WRITE,
                    Map.of("recipientId", "U1001", "message", "hello", "simulateFailures", 2),
                    "idem:notify:T-RETRY-001:1",
                    1));

            support.processor.processPendingTasks();

            assertThat(support.queue.deadLetters()).isEmpty();
            assertThat(support.logStore.logs().stream().filter(log -> log.getStatus() == ToolCallStatus.RETRYING).count()).isEqualTo(2);
            assertThat(support.logStore.logs().stream().filter(log -> log.getStatus() == ToolCallStatus.SUCCESS).count()).isEqualTo(1);
        }
    }

    @Test
    void shouldMoveTaskToDeadLetterAfterMaxRetries() {
        try (HarnessTestSupport support = new HarnessTestSupport()) {
            support.ticketPort.put("T-DLQ-001", TicketStatus.EXECUTING);
            support.planExecutionTracker.register("plan-dlq-001", java.util.Set.of(1));
            support.queue.publish(new ToolExecutionTask(
                    "T-DLQ-001",
                    "plan-dlq-001",
                    1,
                    "NotificationTool",
                    "sendNotification",
                    ToolActionType.WRITE,
                    Map.of("recipientId", "U1001", "message", "hello", "simulateFailures", 5),
                    "idem:notify:T-DLQ-001:1",
                    1));

            support.processor.processPendingTasks();

            assertThat(support.queue.deadLetters()).hasSize(1);
            assertThat(support.ticketPort.getCurrentStatus("T-DLQ-001")).isEqualTo(TicketStatus.ESCALATED);
            assertThat(support.logStore.logs().stream().filter(log -> log.getStatus() == ToolCallStatus.DEAD_LETTER).count()).isEqualTo(1);
        }
    }
}
