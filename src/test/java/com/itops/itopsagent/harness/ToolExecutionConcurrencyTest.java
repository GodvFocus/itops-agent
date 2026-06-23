package com.itops.itopsagent.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.ToolActionType;
import com.itops.itopsagent.harness.support.HarnessTestSupport;
import com.itops.itopsagent.service.harness.ProcessorOutcome;
import com.itops.itopsagent.service.harness.ProcessorOutcomeType;
import com.itops.itopsagent.service.harness.ToolExecutionTask;
import com.itops.itopsagent.service.harness.ToolGateway;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ToolExecutionConcurrencyTest {

    @Test
    void shouldAllowOnlyOneWorkerToExecuteSameTicketAtATime() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger executeCount = new AtomicInteger();
        ToolGateway blockingGateway = task -> {
            executeCount.incrementAndGet();
            firstEntered.countDown();
            try {
                releaseFirst.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("测试线程被中断", exception);
            }
            return Map.of("ok", true);
        };

        try (HarnessTestSupport support = new HarnessTestSupport(blockingGateway)) {
            support.ticketPort.put("T-CONCURRENT-001", TicketStatus.EXECUTING);
            support.planExecutionTracker.register("plan-concurrent-001", java.util.Set.of(1));

            ToolExecutionTask task = new ToolExecutionTask(
                    "T-CONCURRENT-001",
                    "plan-concurrent-001",
                    1,
                    "AccountTool",
                    "unlockAccount",
                    ToolActionType.WRITE,
                    Map.of("employeeId", "U1003"),
                    "idem:unlockAccount:T-CONCURRENT-001:U1003",
                    1);

            ExecutorService executorService = Executors.newFixedThreadPool(2);
            try {
                Future<ProcessorOutcome> first = executorService.submit(() -> support.worker.process(task));
                firstEntered.await(1, TimeUnit.SECONDS);
                Future<ProcessorOutcome> second = executorService.submit(() -> support.worker.process(task));
                releaseFirst.countDown();

                ProcessorOutcome firstOutcome = first.get(2, TimeUnit.SECONDS);
                ProcessorOutcome secondOutcome = second.get(2, TimeUnit.SECONDS);

                assertThat(executeCount.get()).isEqualTo(1);
                assertThat(firstOutcome.type()).isEqualTo(ProcessorOutcomeType.COMPLETED);
                assertThat(secondOutcome.type()).isEqualTo(ProcessorOutcomeType.REQUEUE);
            } finally {
                executorService.shutdownNow();
            }
        }
    }
}
