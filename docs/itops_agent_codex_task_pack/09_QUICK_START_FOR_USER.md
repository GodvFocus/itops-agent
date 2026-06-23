# How to Use This Task Pack with Codex

## 第一次给 Codex 的推荐指令

把整个任务包放进项目根目录，例如：

```text
docs/itops_agent_codex_task_pack/
```

然后对 Codex 说：

```text
请先阅读 docs/itops_agent_codex_task_pack/prompts/CODEX_MASTER_PROMPT.md，并严格按其中规则执行。
当前不要写代码，只返回你对项目目标、MVP边界、技术架构、阶段计划、停止条件的理解摘要。
```

## 开始 Phase 1

```text
请执行 docs/itops_agent_codex_task_pack/phases/phase_01_ticket_state_machine.md。
只做 Phase 1，不要实现 Agent、LLM、Qdrant、Redis 幂等或 RabbitMQ 工具执行。
完成后按 docs/itops_agent_codex_task_pack/templates/EVIDENCE_REPORT_TEMPLATE.md 返回证据。
```

## 开始 Phase 2

```text
请执行 docs/itops_agent_codex_task_pack/phases/phase_02_ai_understanding_context.md。
前提是 Phase 1 已通过验收。只做 Agent 理解层和 Context Builder，不要做 SOP 检索、工具调用或审批流。
完成后按证据模板返回。
```

## 开始 Phase 3

```text
请执行 docs/itops_agent_codex_task_pack/phases/phase_03_sop_retrieval_plan.md。
只实现 SOP 检索和 Candidate Plan 生成，不要执行工具。
完成后按证据模板返回。
```

## 开始 Phase 4

```text
请执行 docs/itops_agent_codex_task_pack/phases/phase_04_harness_tool_async.md。
重点实现 Java Enterprise Harness、Tool Gateway、RabbitMQ 异步工具执行、Redis 锁、幂等和审计。
严禁 Python Agent 直接调用工具。
完成后按证据模板返回。
```

## 开始 Phase 5

```text
请执行 docs/itops_agent_codex_task_pack/phases/phase_05_approval_frontend_e2e.md。
完成审批闭环、前端时间线、用户确认和三条 E2E 演示链路。
完成后按证据模板返回。
```
