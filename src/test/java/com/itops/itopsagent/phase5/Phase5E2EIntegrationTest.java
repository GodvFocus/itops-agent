package com.itops.itopsagent.phase5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.mapper.ApprovalTaskMapper;
import com.itops.itopsagent.mapper.AuditLogMapper;
import com.itops.itopsagent.mapper.AgentStepLogMapper;
import com.itops.itopsagent.mapper.ConversationMessageMapper;
import com.itops.itopsagent.mapper.IdempotencyRecordMapper;
import com.itops.itopsagent.mapper.TicketContextMapper;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import com.itops.itopsagent.mapper.ToolCallLogMapper;
import com.itops.itopsagent.service.harness.ToolTaskProcessor;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest
@AutoConfigureMockMvc
class Phase5E2EIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ToolTaskProcessor toolTaskProcessor;

    @Autowired
    private ApprovalTaskMapper approvalTaskMapper;

    @Autowired
    private ToolCallLogMapper toolCallLogMapper;

    @Autowired
    private IdempotencyRecordMapper idempotencyRecordMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private AgentStepLogMapper agentStepLogMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private TicketContextMapper ticketContextMapper;

    @Autowired
    private TicketStatusHistoryMapper ticketStatusHistoryMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @BeforeEach
    void setUp() {
        approvalTaskMapper.deleteAllRecords();
        toolCallLogMapper.deleteAllRecords();
        idempotencyRecordMapper.deleteAllRecords();
        auditLogMapper.deleteAllRecords();
        agentStepLogMapper.deleteAllRecords();
        conversationMessageMapper.deleteAllRecords();
        ticketContextMapper.deleteAllRecords();
        ticketStatusHistoryMapper.deleteAllRecords();
        ticketMapper.deleteAllRecords();
    }

    @Test
    void shouldCloseTicketAfterAccountUnlockFlow() throws Exception {
        String ticketId = createTicket("OA 登录失败", "我登录不上 OA 了，提示账号被锁定，我的工号是 E10086。");
        awaitToolIdle();

        JsonNode beforeConfirm = getTicket(ticketId);
        assertThat(beforeConfirm.path("status").asText()).isEqualTo("WAITING_USER_CONFIRM");

        JsonNode timeline = getTimeline(ticketId);
        assertThat(timeline.path("resolutionSummary").asText()).contains("自动解锁");
        assertThat(findToolCall(timeline, "AccountTool", "unlockAccount")).isNotNull();

        JsonNode closed = postJson("/api/tickets/" + ticketId + "/confirm", Map.of(
                "resolved", true,
                "comment", "已经可以登录")).responseJson();
        assertThat(closed.path("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    void shouldAskForEmployeeIdAndDiagnoseVpnMfaIssue() throws Exception {
        String ticketId = createTicket("VPN 无法连接", "VPN 连不上，提示认证失败，昨天换过手机。");

        JsonNode initial = getTicket(ticketId);
        assertThat(initial.path("status").asText()).isEqualTo("NEED_MORE_INFO");
        assertThat(initial.path("ticketContext").path("missingSlots").toString()).contains("employeeId");

        postJson("/api/tickets/" + ticketId + "/messages", Map.of("content", "我的员工编号是 E10086，设备是手机")).assertOk();
        awaitToolIdle();

        JsonNode afterReply = getTicket(ticketId);
        assertThat(afterReply.path("status").asText()).isEqualTo("WAITING_USER_CONFIRM");

        JsonNode timeline = getTimeline(ticketId);
        assertThat(timeline.path("matchedSopIds").toString()).contains("SOP-VPN-AUTH-FAIL-001");
        assertThat(timeline.path("resolutionSummary").asText()).contains("MFA");
        assertThat(findToolCall(timeline, "VpnTool", "queryVpnLoginFailure")).isNotNull();
        assertThat(findToolCall(timeline, "MfaTool", "queryMfaStatus")).isNotNull();
    }

    @Test
    void shouldCreateApprovalAndResumeAfterApprove() throws Exception {
        String ticketId = createTicket("生产数据库管理员权限申请", "我需要生产数据库管理员权限，原因是用于线上排障，时长两天。我的工号是 E10086。");

        JsonNode waitingApproval = getTicket(ticketId);
        assertThat(waitingApproval.path("status").asText()).isEqualTo("WAITING_APPROVAL");

        JsonNode approvals = getApprovals(ticketId);
        assertThat(approvals.size()).isEqualTo(1);
        String approvalId = approvals.get(0).path("approvalId").asText();

        postJson("/api/approvals/" + approvalId + "/approve", Map.of(
                "approverId", "AP2001",
                "comment", "变更窗口内允许执行")).assertOk();
        awaitToolIdle();

        JsonNode afterApprove = getTicket(ticketId);
        assertThat(afterApprove.path("status").asText()).isEqualTo("WAITING_USER_CONFIRM");

        JsonNode timeline = getTimeline(ticketId);
        assertThat(timeline.path("resolutionSummary").asText()).contains("权限授予");
        assertThat(findToolCall(timeline, "PermissionTool", "grantPermission")).isNotNull();
    }

    @Test
    void shouldEscalateToManualWhenUserSaysNotResolved() throws Exception {
        String ticketId = createTicket("OA 登录失败", "我登录不上 OA 了，提示账号被锁定，我的工号是 E10086。");
        awaitToolIdle();

        JsonNode manual = postJson("/api/tickets/" + ticketId + "/confirm", Map.of(
                "resolved", false,
                "comment", "还是登不上")).responseJson();
        assertThat(manual.path("status").asText()).isEqualTo("MANUAL_TAKEOVER");
    }

    @Test
    void shouldEscalateWhenApprovalIsRejected() throws Exception {
        String ticketId = createTicket("生产数据库管理员权限申请", "我需要生产数据库管理员权限，原因是用于线上排障，时长两天。我的工号是 E10086。");

        JsonNode approvals = getApprovals(ticketId);
        String approvalId = approvals.get(0).path("approvalId").asText();

        postJson("/api/approvals/" + approvalId + "/reject", Map.of(
                "approverId", "AP2002",
                "comment", "审批依据不足")).assertOk();

        JsonNode escalated = getTicket(ticketId);
        assertThat(escalated.path("status").asText()).isEqualTo("ESCALATED");
    }

    private String createTicket(String title, String description) throws Exception {
        JsonNode response = postJson("/api/tickets", Map.of(
                "title", title,
                "description", description,
                "creatorId", "U1001",
                "creatorRole", "EMPLOYEE")).responseJson();
        return response.path("ticketId").asText();
    }

    private JsonNode getTicket(String ticketId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/tickets/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode getTimeline(String ticketId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/tickets/{ticketId}/timeline", ticketId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode getApprovals(String ticketId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/approvals").param("ticketId", ticketId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode findToolCall(JsonNode timeline, String tool, String action) {
        for (JsonNode toolCall : timeline.path("toolCalls")) {
            if (tool.equals(toolCall.path("toolName").asText()) && action.equals(toolCall.path("actionName").asText())) {
                return toolCall;
            }
        }
        return null;
    }

    private void awaitToolIdle() {
        assertThat(toolTaskProcessor.awaitIdle(Duration.ofSeconds(5))).isTrue();
    }

    private JsonRequestResult postJson(String path, Object payload) throws Exception {
        ResultMatcher expectedStatus = "/api/tickets".equals(path) ? status().isCreated() : status().isOk();
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(expectedStatus)
                .andReturn();
        return new JsonRequestResult(objectMapper.readTree(result.getResponse().getContentAsString()));
    }

    private record JsonRequestResult(JsonNode responseJson) {
        void assertOk() {
            assertThat(responseJson).isNotNull();
        }
    }
}
