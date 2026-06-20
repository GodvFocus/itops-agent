# Phase Plan

MVP 分 5 个阶段执行。Codex 每次只执行一个阶段。完成阶段后必须停止并返回证据报告。

## Phase 1: Ticket System Skeleton and State Machine

目标：搭建 Spring Boot 工单系统骨架、MySQL 表、状态机、基础审计、基础前端。

## Phase 2: AI Understanding and Context Management

目标：接入 Python Agent Runtime、LLM Client 抽象、LangGraph 基础工作流、意图识别、槽位抽取、缺失字段追问、Context Builder。

## Phase 3: SOP Retrieval and Plan Generation

目标：接入 Qdrant，建立 SOP 元数据和向量检索，生成结构化 Candidate Plan，但不执行工具。

## Phase 4: Enterprise Harness, Tool Gateway, Async Execution

目标：实现 Java Harness、Tool Registry、Risk Policy、Tool Gateway、RabbitMQ 异步工具执行、Redis 锁、幂等、ToolCallLog。

## Phase 5: Approval Loop, Frontend Timeline, E2E Demo

目标：实现审批闭环、审批后恢复执行、用户确认、Agent 时间线、三条 E2E 演示链路和评测脚本。

## 阶段停止原则

每个阶段完成本阶段验收标准后必须停止，不要提前实现下一阶段范围。
