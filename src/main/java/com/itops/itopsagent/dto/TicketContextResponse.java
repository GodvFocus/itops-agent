package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TicketContextResponse(
        /** 当前意图。 */
        TicketIntent intent,
        /** 已确认槽位。 */
        Map<String, Object> slots,
        /** 当前缺失槽位。 */
        List<String> missingSlots,
        /** 预留的 SOP ID 列表。 */
        List<String> matchedSopIds,
        /** 预留的当前计划。 */
        Map<String, Object> currentPlan,
        /** 当前风险等级。 */
        RiskLevel riskLevel,
        /** 最近一次 Agent 节点。 */
        String lastAgentStep,
        /** 上下文最近更新时间。 */
        Instant updatedAt) {
}
