package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.ToolCallStatus;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ToolExecutionWorker {

    private static final int MAX_ATTEMPTS = 3;
    private final ToolGateway toolGateway;
    private final TicketExecutionLockService ticketExecutionLockService;
    private final HarnessIdempotencyService harnessIdempotencyService;
    private final HarnessToolCallLogService harnessToolCallLogService;
    private final PlanExecutionTracker planExecutionTracker;
    private final HarnessTicketStatePort harnessTicketStatePort;

    public ToolExecutionWorker(
            ToolGateway toolGateway,
            TicketExecutionLockService ticketExecutionLockService,
            HarnessIdempotencyService harnessIdempotencyService,
            HarnessToolCallLogService harnessToolCallLogService,
            PlanExecutionTracker planExecutionTracker,
            HarnessTicketStatePort harnessTicketStatePort) {
        this.toolGateway = toolGateway;
        this.ticketExecutionLockService = ticketExecutionLockService;
        this.harnessIdempotencyService = harnessIdempotencyService;
        this.harnessToolCallLogService = harnessToolCallLogService;
        this.planExecutionTracker = planExecutionTracker;
        this.harnessTicketStatePort = harnessTicketStatePort;
    }

    public ProcessorOutcome process(ToolExecutionTask task) {
        String owner = task.planId() + ":" + task.stepNo() + ":" + task.attemptNo();
        if (!ticketExecutionLockService.tryAcquire(task.ticketId(), owner)) {
            return ProcessorOutcome.requeue(task);
        }
        try {
            IdempotencyClaim claim = harnessIdempotencyService.claim(task);
            if (claim.status() == IdempotencyClaimStatus.LOCKED) {
                harnessToolCallLogService.record(
                        task.ticketId(),
                        task.planId(),
                        task.stepNo(),
                        task.tool(),
                        task.action(),
                        task.actionType(),
                        task.idemKey(),
                        ToolCallStatus.SKIPPED,
                        "LOCKED",
                        task.params(),
                        null,
                        "同一 idem_key 正在处理中",
                        task.attemptNo());
                return ProcessorOutcome.requeue(task);
            }
            if (claim.status() == IdempotencyClaimStatus.DUPLICATE) {
                harnessToolCallLogService.record(
                        task.ticketId(),
                        task.planId(),
                        task.stepNo(),
                        task.tool(),
                        task.action(),
                        task.actionType(),
                        task.idemKey(),
                        ToolCallStatus.DUPLICATE,
                        "APPROVED",
                        task.params(),
                        Map.of("cachedResult", claim.resultJson()),
                        null,
                        task.attemptNo());
                onStepCompleted(task);
                return ProcessorOutcome.completed();
            }

            Map<String, Object> result = toolGateway.execute(task);
            harnessIdempotencyService.markSuccess(task, result);
            harnessToolCallLogService.record(
                    task.ticketId(),
                    task.planId(),
                    task.stepNo(),
                    task.tool(),
                    task.action(),
                    task.actionType(),
                    task.idemKey(),
                    ToolCallStatus.SUCCESS,
                    "APPROVED",
                    task.params(),
                    result,
                    null,
                    task.attemptNo());
            onStepCompleted(task);
            return ProcessorOutcome.completed();
        } catch (Exception exception) {
            harnessIdempotencyService.markFailure(task, exception.getMessage());
            if (task.attemptNo() < MAX_ATTEMPTS) {
                ToolExecutionTask retryTask = new ToolExecutionTask(
                        task.ticketId(),
                        task.planId(),
                        task.stepNo(),
                        task.tool(),
                        task.action(),
                        task.actionType(),
                        task.params(),
                        task.idemKey(),
                        task.attemptNo() + 1);
                harnessToolCallLogService.record(
                        task.ticketId(),
                        task.planId(),
                        task.stepNo(),
                        task.tool(),
                        task.action(),
                        task.actionType(),
                        task.idemKey(),
                        ToolCallStatus.RETRYING,
                        "APPROVED",
                        task.params(),
                        null,
                        exception.getMessage(),
                        task.attemptNo());
                return ProcessorOutcome.retry(retryTask);
            }
            harnessToolCallLogService.record(
                    task.ticketId(),
                    task.planId(),
                    task.stepNo(),
                    task.tool(),
                    task.action(),
                    task.actionType(),
                    task.idemKey(),
                    ToolCallStatus.DEAD_LETTER,
                    "ESCALATE",
                    task.params(),
                    null,
                    exception.getMessage(),
                    task.attemptNo());
            planExecutionTracker.markPlanFailed(task.planId());
            harnessTicketStatePort.transition(task.ticketId(), TicketStatus.ESCALATED, "工具执行多次失败，已升级人工处理");
            return ProcessorOutcome.deadLetter(task);
        } finally {
            ticketExecutionLockService.release(task.ticketId(), owner);
        }
    }

    private void onStepCompleted(ToolExecutionTask task) {
        if (planExecutionTracker.markStepCompleted(task.planId(), task.stepNo())) {
            harnessTicketStatePort.transition(task.ticketId(), TicketStatus.WAITING_USER_CONFIRM, "工具执行完成，等待用户确认");
        }
    }
}
