package com.itops.itopsagent.controller;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.HarnessDecisionResponse;
import com.itops.itopsagent.service.harness.HarnessPlanValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/harness/plans")
@RequiredArgsConstructor
public class HarnessController {

    private final HarnessPlanValidationService harnessPlanValidationService;

    /**
     * validate 只返回 Harness 裁决结果，方便前端或 Agent 先预览是否会被拦截或进入审批。
     */
    @PostMapping("/validate")
    public HarnessDecisionResponse validatePlan(@RequestBody CandidatePlanRequest request) {
        return harnessPlanValidationService.validatePlan(request);
    }

    /**
     * execute 会在通过校验后把工具任务交给异步执行器，由 Java Harness 统一接管幂等、锁和审计。
     */
    @PostMapping("/execute")
    public HarnessDecisionResponse executePlan(@RequestBody CandidatePlanRequest request) {
        return harnessPlanValidationService.executePlan(request);
    }
}
