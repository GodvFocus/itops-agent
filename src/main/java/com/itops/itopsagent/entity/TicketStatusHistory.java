package com.itops.itopsagent.entity;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ticket_status_history")
public class TicketStatusHistory {

    /** 状态历史记录主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的工单 ID。 */
    @Column(name = "ticket_id", nullable = false, length = 32)
    private String ticketId;

    /** 变更前状态，创建工单时允许为空。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private TicketStatus fromStatus;

    /** 变更后状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private TicketStatus toStatus;

    /** 发起本次状态变更的操作者 ID。 */
    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    /** 发起本次状态变更的操作者角色。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 32)
    private UserRole actorRole;

    /** 本次状态变更备注。 */
    @Column(name = "comment_text", length = 1000)
    private String comment;

    /** 状态变更发生时间。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
