package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.TicketStatus;

public record CreateTicketResponse(
        /** 新创建的工单 ID。 */
        String ticketId,
        /** 工单初始状态。 */
        TicketStatus status) {
}
