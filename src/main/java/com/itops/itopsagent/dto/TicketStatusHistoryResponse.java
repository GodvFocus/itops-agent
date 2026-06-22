package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import java.time.Instant;

public record TicketStatusHistoryResponse(
        /** 变更前状态。 */
        TicketStatus fromStatus,
        /** 变更后状态。 */
        TicketStatus toStatus,
        /** 操作者 ID。 */
        String actorId,
        /** 操作者角色。 */
        UserRole actorRole,
        /** 变更备注。 */
        String comment,
        /** 变更时间。 */
        Instant createdAt) {
}
