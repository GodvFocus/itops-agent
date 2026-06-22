package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import java.time.Instant;

public record TicketSummaryResponse(
        /** 工单 ID。 */
        String ticketId,
        /** 工单标题。 */
        String title,
        /** 当前状态。 */
        TicketStatus status,
        /** 当前优先级。 */
        TicketPriority priority,
        /** 当前版本号。 */
        long version,
        /** 创建时间。 */
        Instant createdAt,
        /** 最后更新时间。 */
        Instant updatedAt) {
}
