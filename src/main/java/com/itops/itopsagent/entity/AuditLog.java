package com.itops.itopsagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "audit_log")
public class AuditLog {

    /** 审计日志主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的工单 ID。 */
    @Column(name = "ticket_id", nullable = false, length = 32)
    private String ticketId;

    /** 操作者类型，例如 USER、SYSTEM。 */
    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    /** 操作者标识。 */
    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    /** 动作名称，例如 TICKET_CREATED。 */
    @Column(nullable = false, length = 64)
    private String action;

    /** 被操作目标类型。 */
    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    /** 被操作目标标识。 */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    /** 结构化审计详情，使用 JSON 序列化保存。 */
    @Column(name = "detail_json", nullable = false, length = 4000)
    private String detailJson;

    /** 审计事件创建时间。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
