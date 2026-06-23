package com.itops.itopsagent.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.ToolActionType;
import com.itops.itopsagent.entity.enums.ToolCallStatus;
import com.itops.itopsagent.harness.support.HarnessTestSupport;
import com.itops.itopsagent.service.harness.ToolExecutionTask;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HarnessExecutionIdempotencyTest {

    @Test
    void shouldExecuteDuplicateUnlockMessageOnlyOnce() {
        try (HarnessTestSupport support = new HarnessTestSupport()) {
            support.ticketPort.put("T-IDEM-001", TicketStatus.EXECUTING);
            support.planExecutionTracker.register("plan-idem-001", java.util.Set.of(1));

            ToolExecutionTask task = new ToolExecutionTask(
                    "T-IDEM-001",
                    "plan-idem-001",
                    1,
                    "AccountTool",
                    "unlockAccount",
                    ToolActionType.WRITE,
                    Map.of("employeeId", "U1001"),
                    "idem:unlockAccount:T-IDEM-001:U1001",
                    1);
            for (int index = 0; index < 10; index++) {
                support.queue.publish(task);
            }

            support.processor.processPendingTasks();
            assertThat(support.processor.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(support.idempotencyStore.records()).hasSize(1);
            assertThat(support.idempotencyStore.records().values().iterator().next().getStatus().name()).isEqualTo("SUCCESS");
            assertThat(support.logStore.logs().stream().filter(log -> log.getStatus() == ToolCallStatus.SUCCESS).count()).isEqualTo(1);
        }
    }
}
