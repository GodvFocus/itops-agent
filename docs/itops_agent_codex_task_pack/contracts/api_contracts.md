# API Contracts

API 具体路径可调整，但语义必须保持。

## Backend Ticket API

### POST /api/tickets

创建工单。

Request:

```json
{
  "title": "VPN 连不上",
  "description": "VPN 连不上，提示认证失败",
  "creatorId": "U1001"
}
```

Response:

```json
{
  "ticketId": "T20260620001",
  "status": "NEW"
}
```

### GET /api/tickets/{ticketId}

查询工单详情。

### GET /api/tickets

查询工单列表。

### POST /api/tickets/{ticketId}/messages

用户补充信息。

### POST /api/tickets/{ticketId}/confirm

用户确认是否解决。

Request:

```json
{
  "resolved": true,
  "comment": "已经可以登录"
}
```

## Context API

### GET /api/agent/context/{ticketId}

Python Agent 获取工单上下文。

### POST /api/agent/context/{ticketId}

Python Agent 写回结构化上下文。

## Harness API

### POST /api/harness/plans/validate

Python 提交 Candidate Plan 给 Java Harness。

Request: see `agent_plan.schema.json`

Response: see `harness_decision.schema.json`

### POST /api/harness/tools/execute

由 Harness 或 Tool Worker 执行工具。Python 不应直接调用此接口，除非作为 Java Harness 内部流转模拟。

## Approval API

### GET /api/approvals

审批任务列表。

### POST /api/approvals/{approvalId}/approve

审批通过。

### POST /api/approvals/{approvalId}/reject

审批拒绝。
