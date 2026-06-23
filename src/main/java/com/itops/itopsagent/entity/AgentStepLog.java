package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itops.itopsagent.entity.enums.AgentStepStatus;
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
@TableName("agent_step_log")
public class AgentStepLog {

    /** Agent 节点日志主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 ID。 */
    @TableField("ticket_id")
    private String ticketId;

    /** 节点名称，例如 classify_intent。 */
    @TableField("node_name")
    private String nodeName;

    /** 输入上下文哈希，用于快速定位相同上下文下的推理结果。 */
    @TableField("input_context_hash")
    private String inputContextHash;

    /** 节点结构化输出 JSON。 */
    @TableField("output_json")
    private String outputJson;

    /** 节点执行状态。 */
    private AgentStepStatus status;

    /** 节点失败时的错误信息。 */
    @TableField("error_message")
    private String errorMessage;

    /** 日志写入时间。 */
    @TableField("created_at")
    private Instant createdAt;
}
