package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.UserRole;

public record CreateTicketRequest(
        /** 工单标题。 */
        String title,
        /** 工单问题描述。 */
        String description,
        /** 创建人 ID。 */
        String creatorId,
        /** 创建人角色，未传时默认按员工处理。 */
        UserRole creatorRole,
        /** 工单优先级，未传时默认使用 MEDIUM。 */
        TicketPriority priority) {
}
