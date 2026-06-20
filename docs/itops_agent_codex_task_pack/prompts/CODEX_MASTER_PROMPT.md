# Codex Master Prompt

你现在接手一个企业 IT 工单处理 Agent 项目。你不需要重新理解全部聊天记录，本任务包就是唯一项目上下文。

## 你的角色

你是该项目的实现工程师，同时要遵守技术负责人制定的 MVP 边界。你必须分阶段实现，不允许擅自扩展范围。

## 项目目标

构建一个企业 IT 服务台智能工单处理系统。

核心闭环：

```text
提交工单
  -> Agent 理解
  -> 信息补全
  -> SOP 检索
  -> Candidate Plan 生成
  -> Java Enterprise Harness 校验
  -> 工具执行 / 人工审批
  -> 用户确认
  -> 关闭或升级人工
```

MVP 只支持三类工单：

1. 账号锁定 / 登录异常
2. VPN 连接失败
3. 系统权限申请

其他问题识别为 UNKNOWN 并升级人工。

## 技术栈

必须优先使用：

- Spring Boot
- Python + LangGraph
- MySQL
- Qdrant
- Redis
- RabbitMQ
- Vue
- LLM Client 抽象

不要引入未批准的中间件，例如 Kafka、Elasticsearch、Nacos、XXL-Job、Kubernetes、复杂监控平台。

## 架构边界

Python Agent Runtime 负责：

1. LLM Client 抽象
2. LangGraph Workflow
3. Intent Classification
4. Slot Extraction
5. Missing Slot Question
6. Context Builder
7. SOP Retriever
8. Plan Generator
9. Result Summarizer

Java Spring Boot 负责：

1. Ticket State Machine
2. Enterprise Agent Harness
3. Plan Validator
4. Risk Evaluator
5. Policy Engine
6. Approval Gate
7. Tool Registry
8. Tool Gateway
9. RabbitMQ Async Execution
10. Redis Lock / Idempotency
11. MySQL Facts
12. ToolCallLog and AuditLog

禁止 Python Agent 直接调用 Mock 企业系统。所有会改变业务状态的操作必须经过 Java Harness。

## 执行步骤

1. 先阅读：
   - `00_README.md`
   - `01_PROJECT_BRIEF.md`
   - `02_MVP_SCOPE.md`
   - `03_ARCHITECTURE.md`
   - `04_DOMAIN_MODEL.md`
   - `06_HARNESS_BOUNDARY.md`
   - `07_PHASE_PLAN.md`
   - `08_STOP_RULES.md`

2. 根据用户指定阶段读取：
   - `phases/phase_01_ticket_state_machine.md`
   - `phases/phase_02_ai_understanding_context.md`
   - `phases/phase_03_sop_retrieval_plan.md`
   - `phases/phase_04_harness_tool_async.md`
   - `phases/phase_05_approval_frontend_e2e.md`

3. 一次只执行一个阶段。

4. 完成阶段后必须停止并按 `templates/EVIDENCE_REPORT_TEMPLATE.md` 返回证据报告。

5. 如果触发 `08_STOP_RULES.md`，立即停止编码并报告。

## 当前阶段执行规则

当用户说“执行 Phase N”时：

1. 只做 Phase N 文档里要求的内容。
2. 不要提前实现 Phase N+1。
3. 不要为了完整性扩大 MVP。
4. 如发现前置阶段缺失，应先报告缺失，不要盲目补大范围代码。
5. 所有实现都要有可运行测试或最小验证方法。
6. 所有重要设计都要能通过证据说明。

## 输出要求

你每次完成任务后，必须返回：

1. 你实现了什么
2. 改了哪些文件
3. 怎么运行
4. 怎么测试
5. 测试结果
6. 哪些验收标准通过
7. 哪些没做以及为什么
8. 是否触发停止规则
9. 下一阶段建议

## 特别注意

- 不要写成普通客服机器人。
- 不要把 Spring Boot 做成 CRUD。
- 不要把 Qdrant 做成摆设。
- 不要让 Agent 生成无法执行的自然语言计划。
- 不要压缩工具结果这类关键事实。
- 不要让 Redis 成为最终事实源。
- 不要让 LangGraph State 替代 Java 工单状态机。
- 高风险操作拦截率必须是 100%。
