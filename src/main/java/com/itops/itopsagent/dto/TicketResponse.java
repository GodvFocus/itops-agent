package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import java.time.Instant;
import java.util.List;

public record TicketResponse(
        /** 工单 ID。 */
        String ticketId,
        /** 工单标题。 */
        String title,
        /** 工单详细描述。 */
        String description,
        /** 创建人 ID。 */
        String creatorId,
        /** 创建人角色。 */
        UserRole creatorRole,
        /** 当前工单状态。 */
        TicketStatus status,
        /** 当前识别到的工单意图。 */
        TicketIntent intent,
        /** 工单优先级。 */
        TicketPriority priority,
        /** 风险等级。 */
        RiskLevel riskLevel,
        /** 当前指派处理人。 */
        String assignedTo,
        /** 当前乐观锁版本。 */
        long version,
        /** 创建时间。 */
        Instant createdAt,
        /** 最后更新时间。 */
        Instant updatedAt,
        /** 关闭时间。 */
        Instant closedAt,
        /** 状态变更历史。 */
        List<TicketStatusHistoryResponse> statusHistory) {
}
