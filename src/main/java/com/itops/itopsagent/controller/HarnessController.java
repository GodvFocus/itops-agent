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
     * Phase 3 只做接收和基础校验。
     * 这样可以先锁住 Agent 与 Harness 的契约，再把真实执行和审批放到 Phase 4。
     */
    @PostMapping("/validate")
    public HarnessDecisionResponse validatePlan(@RequestBody CandidatePlanRequest request) {
        return harnessPlanValidationService.validatePlan(request);
    }
}
