package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.HarnessDecisionResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HarnessPolicyEngine {

    public HarnessPlanEvaluation buildEvaluation(CandidatePlanRequest request, List<PlanStepAssessment> assessments) {
        List<PlanStepAssessment> rejectedSteps = assessments.stream()
                .filter(step -> step.decision() == StepDecision.REJECTED)
                .toList();
        List<PlanStepAssessment> approvalSteps = assessments.stream()
                .filter(step -> step.decision() == StepDecision.NEED_APPROVAL)
                .toList();
        List<PlanStepAssessment> executableSteps = assessments.stream()
                .filter(step -> step.decision() == StepDecision.APPROVED)
                .toList();

        List<java.util.Map<String, Object>> approvedResponseSteps = assessments.stream()
                .filter(step -> step.decision() != StepDecision.REJECTED)
                .map(PlanStepAssessment::toResponseMap)
                .toList();
        List<java.util.Map<String, Object>> rejectedResponseSteps = rejectedSteps.stream()
                .map(PlanStepAssessment::toResponseMap)
                .toList();

        if (!rejectedSteps.isEmpty()) {
            return new HarnessPlanEvaluation(
                    new HarnessDecisionResponse(
                            request.ticketId(),
                            request.planId(),
                            "REJECTED",
                            "NONE",
                            "Plan 被 Harness 风险策略拒绝。",
                            null,
                            rejectedResponseSteps,
                            approvedResponseSteps),
                    List.of(),
                    List.of(),
                    rejectedSteps);
        }
        if (request.intent() != null && request.intent().name().equals("UNKNOWN")) {
            return new HarnessPlanEvaluation(
                    new HarnessDecisionResponse(
                            request.ticketId(),
                            request.planId(),
                            "ESCALATE",
                            "NONE",
                            "UNKNOWN 工单不进入自动执行，需升级人工。",
                            null,
                            List.of(),
                            approvedResponseSteps),
                    List.of(),
                    List.of(),
                    List.of());
        }
        if (!approvalSteps.isEmpty()) {
            return new HarnessPlanEvaluation(
                    new HarnessDecisionResponse(
                            request.ticketId(),
                            request.planId(),
                            "NEED_APPROVAL",
                            "PAUSE",
                            "Plan 命中高风险步骤，需等待审批。",
                            "MANUAL_APPROVAL",
                            List.of(),
                            approvedResponseSteps),
                    List.of(),
                    approvalSteps,
                    List.of());
        }
        return new HarnessPlanEvaluation(
                new HarnessDecisionResponse(
                        request.ticketId(),
                        request.planId(),
                        "APPROVED",
                        "ASYNC",
                        "Plan 已通过 Harness 审核，进入异步工具执行。",
                        null,
                        List.of(),
                        approvedResponseSteps),
                executableSteps,
                List.of(),
                List.of());
    }
}
