package com.itops.itopsagent.harness.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.controller.GlobalExceptionHandler;
import com.itops.itopsagent.controller.HarnessController;
import com.itops.itopsagent.service.harness.HarnessPlanValidationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
class HarnessControllerWebMvcTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        HarnessPlanValidationService service = new HarnessPlanValidationService();
        this.mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new HarnessController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldApproveLowRiskCandidatePlan() throws Exception {
        Map<String, Object> request = Map.of(
                "planId", "plan-approve-001",
                "ticketId", "T-APPROVE-001",
                "intent", "ACCOUNT_LOGIN_ISSUE",
                "riskLevel", "LOW",
                "steps", List.of(
                        Map.of(
                                "stepNo", 1,
                                "tool", "AccountTool",
                                "action", "queryAccountStatus",
                                "actionType", "READ",
                                "params", Map.of("employeeId", "U1001"),
                                "riskLevel", "LOW",
                                "requiredApproval", false,
                                "reason", "先确认账号是否锁定"),
                        Map.of(
                                "stepNo", 2,
                                "tool", "NotificationTool",
                                "action", "sendNotification",
                                "actionType", "WRITE",
                                "params", Map.of("recipientId", "U1001", "message", "候选计划已生成"),
                                "riskLevel", "LOW",
                                "requiredApproval", false,
                                "reason", "通知申请人当前计划")))
        ;

        mockMvc.perform(post("/api/harness/plans/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("APPROVED")))
                .andExpect(jsonPath("$.executionMode", is("NONE")))
                .andExpect(jsonPath("$.approvedSteps", hasSize(2)));
    }

    @Test
    void shouldMarkHighRiskPlanAsNeedApproval() throws Exception {
        Map<String, Object> request = Map.of(
                "planId", "plan-approval-001",
                "ticketId", "T-APPROVAL-001",
                "intent", "PERMISSION_REQUEST",
                "riskLevel", "HIGH",
                "steps", List.of(
                        Map.of(
                                "stepNo", 1,
                                "tool", "PermissionTool",
                                "action", "queryPermission",
                                "actionType", "READ",
                                "params", Map.of("employeeId", "U2001", "targetSystem", "ERP"),
                                "riskLevel", "LOW",
                                "requiredApproval", false,
                                "reason", "先查询当前权限"),
                        Map.of(
                                "stepNo", 2,
                                "tool", "PermissionTool",
                                "action", "grantPermission",
                                "actionType", "WRITE",
                                "params", Map.of("employeeId", "U2001", "targetSystem", "ERP", "permissionLevel", "ADMIN"),
                                "riskLevel", "HIGH",
                                "requiredApproval", true,
                                "reason", "高风险权限必须进入审批")))
        ;

        mockMvc.perform(post("/api/harness/plans/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("NEED_APPROVAL")))
                .andExpect(jsonPath("$.executionMode", is("PAUSE")))
                .andExpect(jsonPath("$.approvalType", is("MANUAL_APPROVAL")));
    }

    @Test
    void shouldRejectPlanWithUnregisteredTool() throws Exception {
        Map<String, Object> request = Map.of(
                "planId", "plan-reject-001",
                "ticketId", "T-REJECT-001",
                "intent", "PERMISSION_REQUEST",
                "riskLevel", "MEDIUM",
                "steps", List.of(
                        Map.of(
                                "stepNo", 1,
                                "tool", "UnknownTool",
                                "action", "doSomething",
                                "actionType", "WRITE",
                                "params", Map.of("employeeId", "U3001"),
                                "riskLevel", "MEDIUM",
                                "requiredApproval", false,
                                "reason", "非法工具测试")))
        ;

        mockMvc.perform(post("/api/harness/plans/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("REJECTED")))
                .andExpect(jsonPath("$.rejectedSteps", hasSize(1)));
    }
}
