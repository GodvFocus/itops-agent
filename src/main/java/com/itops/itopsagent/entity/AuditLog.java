package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("audit_log")
public class AuditLog {

    /** 审计日志主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的工单 ID。 */
    @TableField("ticket_id")
    private String ticketId;

    /** 操作者类型，例如 USER、SYSTEM。 */
    @TableField("actor_type")
    private String actorType;

    /** 操作者标识。 */
    @TableField("actor_id")
    private String actorId;

    /** 动作名称，例如 TICKET_CREATED。 */
    private String action;

    /** 被操作目标类型。 */
    @TableField("target_type")
    private String targetType;

    /** 被操作目标标识。 */
    @TableField("target_id")
    private String targetId;

    /** 结构化审计详情，使用 JSON 序列化保存。 */
    @TableField("detail_json")
    private String detailJson;

    /** 审计事件创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

}
