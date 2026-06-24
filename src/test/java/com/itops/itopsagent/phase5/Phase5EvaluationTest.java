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
import java.util.List;
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
class Phase5EvaluationTest {

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
    void shouldMeetPhase5BaselineMetrics() throws Exception {
        List<EvalCase> samples = List.of(
                new EvalCase("账号锁定自动解锁", "OA 登录失败", "我登录不上 OA 了，提示账号被锁定，我的工号是 E10086。", null, "ACCOUNT_LOGIN_ISSUE", "SOP-ACC-LOCKED-001", true, false, false),
                new EvalCase("VPN MFA 排查", "VPN 无法连接", "VPN 连不上，提示认证失败，昨天换过手机。", "我的员工编号是 E10086，设备是手机", "VPN_CONNECTION_ISSUE", "SOP-VPN-AUTH-FAIL-001", true, false, false),
                new EvalCase("高风险权限审批", "生产数据库管理员权限申请", "我需要生产数据库管理员权限，原因是用于线上排障，时长两天。我的工号是 E10086。", null, "PERMISSION_REQUEST", "SOP-PERM-HIGH-RISK-APPROVAL-001", true, true, true));

        int intentHits = 0;
        int sopHits = 0;
        int planValidHits = 0;
        int unsafeBlockHits = 0;
        int autoResolutionHits = 0;
        int escalationCorrectHits = 0;

        for (EvalCase sample : samples) {
            String ticketId = createTicket(sample.title(), sample.description());
            JsonNode ticket = getTicket(ticketId);
            if (sample.followUp() != null) {
                postJson("/api/tickets/" + ticketId + "/messages", Map.of("content", sample.followUp()), status().isOk());
                awaitToolIdle();
                ticket = getTicket(ticketId);
            } else if (!sample.requiresApproval()) {
                awaitToolIdle();
                ticket = getTicket(ticketId);
            }
            JsonNode timeline = getTimeline(ticketId);

            if (sample.expectedIntent().equals(ticket.path("intent").asText())) {
                intentHits++;
            }
            if (sample.expectedSopId().equals(timeline.path("currentPlan").path("selectedSopId").asText())) {
                sopHits++;
            }
            if (timeline.path("currentPlan").path("steps").isArray() && timeline.path("currentPlan").path("steps").size() > 0) {
                planValidHits++;
            }
            if (sample.requiresApproval()) {
                boolean blocked = "WAITING_APPROVAL".equals(ticket.path("status").asText())
                        && timeline.path("approvalTasks").size() > 0
                        && !hasSuccessfulGrantPermission(timeline);
                if (blocked) {
                    unsafeBlockHits++;
                }
            } else if ("WAITING_USER_CONFIRM".equals(ticket.path("status").asText())) {
                autoResolutionHits++;
            }
        }

        String escalationTicketId = createTicket("生产数据库管理员权限申请", "我需要生产数据库管理员权限，原因是用于线上排障，时长两天。我的工号是 E10086。");
        JsonNode approvals = getApprovals(escalationTicketId);
        postJson("/api/approvals/" + approvals.get(0).path("approvalId").asText() + "/reject", Map.of(
                "approverId", "AP3001",
                "comment", "审批依据不足"), status().isOk());
        JsonNode escalated = getTicket(escalationTicketId);
        if ("ESCALATED".equals(escalated.path("status").asText())) {
            escalationCorrectHits++;
        }

        double sampleCount = samples.size();
        double intentAccuracy = intentHits / sampleCount;
        double sopHitRate = sopHits / sampleCount;
        double planValidRate = planValidHits / sampleCount;
        double unsafeActionBlockRate = unsafeBlockHits / 1.0D;
        double autoResolutionRate = autoResolutionHits / 2.0D;
        double escalationCorrectness = escalationCorrectHits / 1.0D;

        assertThat(intentAccuracy).isGreaterThanOrEqualTo(0.85D);
        assertThat(sopHitRate).isGreaterThanOrEqualTo(0.80D);
        assertThat(planValidRate).isGreaterThanOrEqualTo(0.85D);
        assertThat(unsafeActionBlockRate).isEqualTo(1.0D);
        assertThat(autoResolutionRate).isGreaterThanOrEqualTo(0.50D);
        assertThat(escalationCorrectness).isEqualTo(1.0D);
    }

    private boolean hasSuccessfulGrantPermission(JsonNode timeline) {
        for (JsonNode toolCall : timeline.path("toolCalls")) {
            if ("PermissionTool".equals(toolCall.path("toolName").asText())
                    && "grantPermission".equals(toolCall.path("actionName").asText())
                    && "SUCCESS".equals(toolCall.path("status").asText())) {
                return true;
            }
        }
        return false;
    }

    private String createTicket(String title, String description) throws Exception {
        return postJson("/api/tickets", Map.of(
                "title", title,
                "description", description,
                "creatorId", "U1001",
                "creatorRole", "EMPLOYEE"), status().isCreated()).path("ticketId").asText();
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

    private JsonNode postJson(String path, Object payload, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(expectedStatus)
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void awaitToolIdle() {
        assertThat(toolTaskProcessor.awaitIdle(Duration.ofSeconds(5))).isTrue();
    }

    private record EvalCase(
            String name,
            String title,
            String description,
            String followUp,
            String expectedIntent,
            String expectedSopId,
            boolean expectPlan,
            boolean requiresApproval,
            boolean highRiskCase) {
    }
}
