# Phase 2: AI Understanding and Context Management

## 阶段目标

让 Agent 能理解工单，但不执行工具。

实现：

1. Python Agent Runtime 基础结构
2. LLM Client 抽象
3. MockLLMClient，用于无真实 API 时测试
4. LangGraph 基础工作流
5. Intent Classification
6. Slot Extraction
7. Missing Slot Question
8. Context Builder
9. conversation_message 存储
10. ticket_context 存储
11. agent_step_log 存储

## 本阶段不要实现

不要实现：

1. 工具调用
2. 自动解锁
3. 权限授予
4. Qdrant SOP 检索
5. 审批流恢复执行
6. 长期用户记忆
7. 多 Agent

## 支持 intent

1. ACCOUNT_LOGIN_ISSUE
2. VPN_CONNECTION_ISSUE
3. PERMISSION_REQUEST
4. UNKNOWN

## 槽位要求

### ACCOUNT_LOGIN_ISSUE

required:

1. employeeId
2. targetSystem 或 loginSystem
3. errorMessage 可选

### VPN_CONNECTION_ISSUE

required:

1. employeeId
2. deviceType
3. errorMessage

optional:

1. networkType
2. mfaRecentlyChanged

### PERMISSION_REQUEST

required:

1. employeeId
2. targetSystem
3. permissionLevel
4. reason
5. duration

## 可能涉及模块

Python:

1. llm_client/base.py
2. llm_client/mock.py
3. graph/workflow.py
4. nodes/classify_intent.py
5. nodes/extract_slots.py
6. nodes/generate_question.py
7. context/context_builder.py

Java:

1. ContextController
2. ConversationMessageService
3. TicketContextService
4. AgentStepLogService

## 验收标准

1. 输入账号锁定问题，识别 ACCOUNT_LOGIN_ISSUE
2. 输入 VPN 问题，识别 VPN_CONNECTION_ISSUE
3. 输入权限申请，识别 PERMISSION_REQUEST
4. 不支持的问题识别 UNKNOWN 并升级人工
5. 缺少员工编号时能追问
6. 缺少权限申请原因时能追问
7. 抽取 slots 能写入 ticket_context
8. Agent 每个节点有 agent_step_log
9. 前端能显示 Agent 追问

## 测试要求

准备至少 30 条样例：

1. 10 条账号问题
2. 10 条 VPN 问题
3. 10 条权限申请

测试指标：

1. Intent Accuracy
2. Slot Extraction Accuracy
3. Missing Slot Recall
4. UNKNOWN 识别率

最低验收：

1. Intent Accuracy >= 85%
2. 关键 slot 抽取准确率 >= 80%
3. 缺失字段追问覆盖率 >= 90%

## 风险

1. LLM 输出 JSON 不稳定
2. Prompt 越写越复杂
3. 上下文拼接混乱
4. 过早依赖完整聊天历史

## 控制方式

1. 所有 LLM 输出必须经过 JSON Schema 校验
2. 解析失败要重试或降级
3. 关键事实落 MySQL
4. Context Builder 从本阶段开始设计
