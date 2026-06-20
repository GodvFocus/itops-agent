# RabbitMQ Events

MVP 只需要三类核心消息。

## TicketAgentTask

触发 Agent 处理工单。

```json
{
  "eventType": "TicketAgentTask",
  "ticketId": "T20260620001",
  "reason": "TICKET_CREATED",
  "createdAt": "2026-06-20T10:00:00+09:00"
}
```

## ToolExecutionTask

异步执行工具。

```json
{
  "eventType": "ToolExecutionTask",
  "ticketId": "T20260620001",
  "planId": "P001",
  "stepNo": 1,
  "tool": "AccountTool",
  "action": "queryAccountStatus",
  "params": {
    "employeeId": "E10086"
  },
  "idemKey": null
}
```

## ApprovalResultEvent

审批结果触发 Agent 恢复执行。

```json
{
  "eventType": "ApprovalResultEvent",
  "ticketId": "T20260620001",
  "approvalId": "A001",
  "result": "APPROVED",
  "decidedBy": "U_APPROVER_1"
}
```
