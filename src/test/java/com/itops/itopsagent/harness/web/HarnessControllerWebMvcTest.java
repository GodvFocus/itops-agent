package com.itops.itopsagent.harness.web;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.controller.GlobalExceptionHandler;
import com.itops.itopsagent.controller.HarnessController;
import com.itops.itopsagent.dto.HarnessDecisionResponse;
import com.itops.itopsagent.service.harness.HarnessPlanValidationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HarnessControllerWebMvcTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HarnessPlanValidationService harnessPlanValidationService;

    @BeforeEach
    void setUp() {
        harnessPlanValidationService = Mockito.mock(HarnessPlanValidationService.class);
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new HarnessController(harnessPlanValidationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnAsyncDecisionForExecuteEndpoint() throws Exception {
        when(harnessPlanValidationService.executePlan(any())).thenReturn(new HarnessDecisionResponse(
                "T-EXECUTE-001",
                "plan-execute-001",
                "APPROVED",
                "ASYNC",
                "进入异步执行",
                null,
                List.of(),
                List.of(Map.of("stepNo", 1, "tool", "AccountTool", "action", "unlockAccount"))));

        mockMvc.perform(post("/api/harness/plans/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "planId", "plan-execute-001",
                                "ticketId", "T-EXECUTE-001",
                                "intent", "ACCOUNT_LOGIN_ISSUE",
                                "riskLevel", "MEDIUM",
                                "goal", "解锁账号",
                                "steps", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("APPROVED")))
                .andExpect(jsonPath("$.executionMode", is("ASYNC")));
    }

    @Test
    void shouldReturnApprovalDecisionForValidateEndpoint() throws Exception {
        when(harnessPlanValidationService.validatePlan(any())).thenReturn(new HarnessDecisionResponse(
                "T-APPROVAL-001",
                "plan-approval-001",
                "NEED_APPROVAL",
                "PAUSE",
                "需要审批",
                "MANUAL_APPROVAL",
                List.of(),
                List.of(Map.of("stepNo", 1, "tool", "PermissionTool", "action", "grantPermission"))));

        mockMvc.perform(post("/api/harness/plans/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "planId", "plan-approval-001",
                                "ticketId", "T-APPROVAL-001",
                                "intent", "PERMISSION_REQUEST",
                                "riskLevel", "HIGH",
                                "goal", "申请权限",
                                "steps", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("NEED_APPROVAL")))
                .andExpect(jsonPath("$.approvalType", is("MANUAL_APPROVAL")));
    }
}
