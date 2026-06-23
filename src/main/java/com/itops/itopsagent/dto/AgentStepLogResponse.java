package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.AgentStepStatus;
import java.time.Instant;
import java.util.Map;

public record AgentStepLogResponse(
        /** 节点名称。 */
        String nodeName,
        /** 输入上下文哈希。 */
        String inputContextHash,
        /** 节点输出。 */
        Map<String, Object> output,
        /** 节点状态。 */
        AgentStepStatus status,
        /** 错误信息。 */
        String errorMessage,
        /** 创建时间。 */
        Instant createdAt) {
}
