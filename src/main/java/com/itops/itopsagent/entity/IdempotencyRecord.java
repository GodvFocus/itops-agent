package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itops.itopsagent.entity.enums.IdempotencyStatus;
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
@TableName("idempotency_record")
public class IdempotencyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** idemKey 是最终幂等事实主键，必须与工具动作一一对应。 */
    @TableField("idem_key")
    private String idemKey;

    @TableField("ticket_id")
    private String ticketId;

    @TableField("plan_id")
    private String planId;

    @TableField("step_no")
    private Integer stepNo;

    @TableField("tool_name")
    private String toolName;

    @TableField("action_name")
    private String actionName;

    private IdempotencyStatus status;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
