package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import java.util.List;
import java.util.Map;

public record UpdateAgentContextRequest(
        /** 要写回的意图。 */
        TicketIntent intent,
        /** 槽位快照。 */
        Map<String, Object> slots,
        /** 当前缺失槽位。 */
        List<String> missingSlots,
        /** 预留的 SOP ID。 */
        List<String> matchedSopIds,
        /** 预留的当前计划。 */
        Map<String, Object> currentPlan,
        /** 风险等级。 */
        RiskLevel riskLevel,
        /** 最近节点。 */
        String lastAgentStep) {
}
