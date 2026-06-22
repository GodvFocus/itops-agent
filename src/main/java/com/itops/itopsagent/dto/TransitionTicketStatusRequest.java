package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;

public record TransitionTicketStatusRequest(
        /** 目标状态。 */
        TicketStatus targetStatus,
        /** 操作者 ID。 */
        String actorId,
        /** 操作者角色。 */
        UserRole actorRole,
        /** 调用方预期版本号，用于并发冲突保护。 */
        Long expectedVersion,
        /** 本次状态变更备注。 */
        String comment) {
}
