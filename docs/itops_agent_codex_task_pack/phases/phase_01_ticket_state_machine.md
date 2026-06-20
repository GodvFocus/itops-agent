# Phase 1: Ticket System Skeleton and State Machine

## 阶段目标

实现最小工单系统骨架：

1. Spring Boot 项目基础结构
2. MySQL 数据模型
3. 工单创建 / 查询 / 列表
4. 工单状态机
5. 基础用户角色
6. 状态变更日志
7. 基础审计日志
8. Vue 基础页面：提交工单、工单列表、工单详情

## 本阶段不要实现

不要实现：

1. LLM
2. LangGraph
3. Qdrant
4. RabbitMQ 复杂链路
5. Redis 幂等
6. 工具调用
7. 审批恢复执行
8. 多 Agent
9. 完整权限系统

## 可能涉及模块

Backend:

1. TicketController
2. TicketService
3. TicketStateMachine
4. AuditLogService
5. User / Role 简化实现
6. MySQL migration

Frontend:

1. TicketCreateView
2. TicketListView
3. TicketDetailView

## 验收标准

1. 可以创建工单
2. 可以查看工单列表
3. 可以查看工单详情
4. 状态只能按规则流转
5. 非法状态流转被拒绝
6. 每次状态变化都有日志
7. CLOSED 工单不能重新进入 EXECUTING
8. WAITING_APPROVAL 工单不能直接 CLOSED
9. 基础角色可以区分 EMPLOYEE / IT_ENGINEER / APPROVER / ADMIN

## 测试要求

必须有：

1. 状态机单元测试
2. 工单创建接口测试
3. 工单查询接口测试
4. 非法状态流转测试
5. 并发更新状态测试
6. 审计日志落库测试

## 风险

1. 状态机设计过细导致卡住
2. 前端花太多时间
3. 权限系统做得太重
4. 表结构过度设计

## 完成后必须返回证据

根据 `templates/EVIDENCE_REPORT_TEMPLATE.md` 返回：

1. 改动文件列表
2. API 列表
3. 状态机测试结果
4. 截图或可访问页面说明
5. 未完成项
6. 下一阶段建议
