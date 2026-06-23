package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.HarnessDecisionResponse;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.ToolCallStatus;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HarnessPlanValidationService {

    private final HarnessRiskEvaluator harnessRiskEvaluator;
    private final HarnessPolicyEngine harnessPolicyEngine;
    private final HarnessTicketStatePort harnessTicketStatePort;
    private final HarnessToolCallLogService harnessToolCallLogService;
    private final ToolTaskQueue toolTaskQueue;
    private final ToolTaskProcessor toolTaskProcessor;
    private final PlanExecutionTracker planExecutionTracker;

    public HarnessPlanValidationService(
            HarnessRiskEvaluator harnessRiskEvaluator,
            HarnessPolicyEngine harnessPolicyEngine,
            HarnessTicketStatePort harnessTicketStatePort,
            HarnessToolCallLogService harnessToolCallLogService,
            ToolTaskQueue toolTaskQueue,
            ToolTaskProcessor toolTaskProcessor,
            PlanExecutionTracker planExecutionTracker) {
        this.harnessRiskEvaluator = harnessRiskEvaluator;
        this.harnessPolicyEngine = harnessPolicyEngine;
        this.harnessTicketStatePort = harnessTicketStatePort;
        this.harnessToolCallLogService = harnessToolCallLogService;
        this.toolTaskQueue = toolTaskQueue;
        this.toolTaskProcessor = toolTaskProcessor;
        this.planExecutionTracker = planExecutionTracker;
    }

    public HarnessDecisionResponse validatePlan(CandidatePlanRequest request) {
        return evaluatePlan(request).response();
    }

    public HarnessDecisionResponse executePlan(CandidatePlanRequest request) {
        HarnessPlanEvaluation evaluation = evaluatePlan(request);
        HarnessDecisionResponse response = evaluation.response();
        if ("REJECTED".equals(response.decision())) {
            logRejectedSteps(request, evaluation.rejectedSteps());
            return response;
        }
        if ("ESCALATE".equals(response.decision())) {
            ensurePlanValidating(request.ticketId());
            harnessTicketStatePort.transition(request.ticketId(), TicketStatus.ESCALATED, "Harness 无法安全执行，已升级人工");
            return response;
        }
        if ("NEED_APPROVAL".equals(response.decision())) {
            ensurePlanValidating(request.ticketId());
            logApprovalSteps(request, evaluation.approvalSteps());
            harnessTicketStatePort.transition(request.ticketId(), TicketStatus.WAITING_APPROVAL, "Plan 触发审批门禁");
            return response;
        }

        ensurePlanValidating(request.ticketId());
        planExecutionTracker.register(
                request.planId(),
                evaluation.executableSteps().stream().map(PlanStepAssessment::stepNo).collect(java.util.stream.Collectors.toSet()));
        harnessTicketStatePort.transition(request.ticketId(), TicketStatus.EXECUTING, "Harness 已接管并开始异步执行工具");
        for (PlanStepAssessment step : evaluation.executableSteps()) {
            harnessToolCallLogService.record(
                    request.ticketId(),
                    request.planId(),
                    step.stepNo(),
                    step.tool(),
                    step.action(),
                    step.actionType(),
                    step.idemKey(),
                    ToolCallStatus.QUEUED,
                    "APPROVED",
                    step.params(),
                    null,
                    null,
                    1);
            toolTaskQueue.publish(new ToolExecutionTask(
                    request.ticketId(),
                    request.planId(),
                    step.stepNo(),
                    step.tool(),
                    step.action(),
                    step.actionType(),
                    step.params(),
                    step.idemKey(),
                    1));
        }
        toolTaskProcessor.processPendingAsync();
        return response;
    }

    private HarnessPlanEvaluation evaluatePlan(CandidatePlanRequest request) {
        TicketStatus currentStatus = harnessTicketStatePort.getCurrentStatus(request.ticketId());
        List<PlanStepAssessment> assessments = harnessRiskEvaluator.evaluate(request, currentStatus);
        return harnessPolicyEngine.buildEvaluation(request, assessments);
    }

    private void ensurePlanValidating(String ticketId) {
        TicketStatus currentStatus = harnessTicketStatePort.getCurrentStatus(ticketId);
        if (currentStatus == TicketStatus.PLANNING) {
            harnessTicketStatePort.transition(ticketId, TicketStatus.PLAN_VALIDATING, "Harness 开始校验 Candidate Plan");
        }
    }

    private void logRejectedSteps(CandidatePlanRequest request, List<PlanStepAssessment> rejectedSteps) {
        for (PlanStepAssessment step : rejectedSteps) {
            harnessToolCallLogService.record(
                    request.ticketId(),
                    request.planId(),
                    step.stepNo(),
                    step.tool(),
                    step.action(),
                    step.actionType(),
                    step.idemKey(),
                    ToolCallStatus.REJECTED,
                    "REJECTED",
                    step.params(),
                    null,
                    step.reason(),
                    1);
        }
    }

    private void logApprovalSteps(CandidatePlanRequest request, List<PlanStepAssessment> approvalSteps) {
        for (PlanStepAssessment step : approvalSteps) {
            harnessToolCallLogService.record(
                    request.ticketId(),
                    request.planId(),
                    step.stepNo(),
                    step.tool(),
                    step.action(),
                    step.actionType(),
                    step.idemKey(),
                    ToolCallStatus.PENDING_APPROVAL,
                    "NEED_APPROVAL",
                    step.params(),
                    null,
                    step.reason(),
                    1);
        }
    }
}
