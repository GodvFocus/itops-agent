# Agent Context and Memory

## 设计原则

上下文管理必须前置设计，但复杂压缩可以后置。

不要把完整聊天历史、所有工具日志、SOP 文档一股脑塞给模型。必须通过 Context Builder 按当前节点组装上下文。

## Memory 类型

### Ticket Working Memory

工单级短期记忆，保存：

1. ticketId
2. intent
3. slots
4. missing slots
5. matched SOP
6. plan
7. tool results
8. risk level
9. current agent step

最终事实存 MySQL 的 ticket_context。

### Conversation Memory

保存两层：

1. 原始对话：conversation_message，供前端展示和审计
2. 结构化摘要：conversation_summary，供长对话压缩

### SOP Knowledge Memory

Qdrant 存 SOP 文档片段向量。

MySQL 存 SOP 元数据。

### Similar Resolved Ticket Memory

V1 可选。不是主链路必需。

## Context Builder

每次 LLM 调用通过 Context Builder 组装：

```json
{
  "ticket_facts": {},
  "current_state": {},
  "known_slots": {},
  "missing_slots": [],
  "recent_messages": [],
  "conversation_summary": "",
  "matched_sops": [],
  "tool_evidence": [],
  "approval_context": {},
  "risk_policy": {},
  "current_node": ""
}
```

## 信息优先级

### P0 必须保真的结构化事实

不能有损压缩：

1. ticketId
2. employeeId
3. intent
4. 当前工单状态
5. 已确认 slots
6. 工具调用结果 JSON
7. Harness 决策
8. 审批结果
9. 风险等级

### P1 当前节点相关上下文

可以进入 prompt：

1. 最新用户输入
2. 最近几轮对话
3. 当前 Agent 节点
4. 当前缺失字段
5. 当前候选 SOP
6. 当前执行计划

### P2 可摘要历史

可压缩：

1. 较早对话
2. 已完成步骤自然语言过程
3. Agent 之前解释

### P3 可检索知识

按需从 Qdrant 检索：

1. SOP
2. 错误码说明
3. 历史已解决工单

## V1 压缩规则

1. 最近 6 轮对话保留原文
2. 更早对话压缩成 summary
3. 工具结果 JSON 永远保留
4. SOP 只保留 Top 3 片段
5. 关键事实结构化保存，不依赖模型记忆
