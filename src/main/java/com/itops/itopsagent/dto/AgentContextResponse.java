package com.itops.itopsagent.dto;

import java.util.List;
import java.util.Map;

public record AgentContextResponse(
        /** 工单事实。 */
        Map<String, Object> ticketFacts,
        /** 当前工单状态。 */
        Map<String, Object> currentState,
        /** 已知槽位。 */
        Map<String, Object> knownSlots,
        /** 当前缺失槽位。 */
        List<String> missingSlots,
        /** 最近消息。 */
        List<Map<String, Object>> recentMessages,
        /** 历史摘要。 */
        String conversationSummary,
        /** 匹配 SOP。 */
        List<String> matchedSops,
        /** 工具证据。 */
        List<Map<String, Object>> toolEvidence,
        /** 审批上下文。 */
        Map<String, Object> approvalContext,
        /** 风险策略。 */
        Map<String, Object> riskPolicy,
        /** 当前节点。 */
        String currentNode) {
}
