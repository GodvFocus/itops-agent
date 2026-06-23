package com.itops.itopsagent.service.harness;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class ToolTaskProcessor {

    private final ToolTaskQueue toolTaskQueue;
    private final ToolExecutionWorker toolExecutionWorker;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Set<CompletableFuture<Void>> activeRuns = ConcurrentHashMap.newKeySet();

    public ToolTaskProcessor(ToolTaskQueue toolTaskQueue, ToolExecutionWorker toolExecutionWorker) {
        this.toolTaskQueue = toolTaskQueue;
        this.toolExecutionWorker = toolExecutionWorker;
    }

    public void processPendingAsync() {
        CompletableFuture<Void> future = CompletableFuture.runAsync(this::processPendingTasks, executorService);
        activeRuns.add(future);
        future.whenComplete((unused, throwable) -> activeRuns.remove(future));
    }

    public void processPendingTasks() {
        while (true) {
            ToolExecutionTask task = toolTaskQueue.poll();
            if (task == null) {
                return;
            }
            ProcessorOutcome outcome = toolExecutionWorker.process(task);
            if (outcome.type() == ProcessorOutcomeType.RETRY) {
                toolTaskQueue.publish(outcome.nextTask());
                continue;
            }
            if (outcome.type() == ProcessorOutcomeType.REQUEUE) {
                toolTaskQueue.publish(outcome.nextTask());
                processPendingAsync();
                return;
            }
            if (outcome.type() == ProcessorOutcomeType.DEAD_LETTER && outcome.nextTask() != null) {
                toolTaskQueue.moveToDeadLetter(outcome.nextTask());
            }
        }
    }

    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (toolTaskQueue.pendingCount() == 0 && activeRuns.isEmpty()) {
                return true;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return toolTaskQueue.pendingCount() == 0 && activeRuns.isEmpty();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
