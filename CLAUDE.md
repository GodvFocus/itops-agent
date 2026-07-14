## 代码结构

- `config/`：Spring 配置类
- `controller/`：控制层与全局异常处理
- `dto/`：请求与响应数据结构
- `entity/`：数据库实体与枚举定义
- `interceptor/`：Spring MVC 拦截器
- `mapper/`：持久层接口，统一按 `MyBatis-Plus` 风格组织
- `service/`：业务服务接口
- `service/agent/`：Agent 理解、上下文组装与结构化输出校验
- `service/harness/`：Tool Registry、风险策略、异步工具执行、幂等与 ToolCallLog
- `service/impl/`：业务服务实现
- `utils/`：通用工具与自定义异常
- `agent_runtime/`：Python Agent Runtime，包含节点、检索、计划生成与测试

## 当前阶段事实

- 当前主线已经完成 `Phase 5`，并已推进 P0 Agent 升级：
  - Java 侧已具备 Harness 风险裁决、异步工具执行、内存版队列语义、票据执行锁、MySQL 幂等记录与 `tool_call_log`
  - 已具备 `approval_task`、审批通过恢复执行、审批拒绝升级人工、用户确认关闭
  - 已提供 `GET /api/tickets/{ticketId}/timeline`、`GET /api/approvals`、`POST /api/approvals/{approvalId}/approve|reject`
  - `POST /api/harness/plans/validate` 用于预校验
  - `POST /api/harness/plans/execute` 用于真实进入 Harness 异步执行链路
- Agent 侧已使用 LangGraph StateGraph 实现真实状态图编排（含条件边、节点级 trace）
- Agent 侧已通过 OpenAI 兼容协议接入真实 LLM（支持 DeepSeek/Qwen/GLM 配置化切换）
- RabbitMQ / Redis 当前是"配置入口已预留、实现仍以内存适配器为默认值"
- Qdrant 当前支持通过统一命名配置接真实地址；未配置时回退到内存向量仓储

## 运行配置约定

- Java 侧统一配置文件为 `src/main/resources/application.yaml`
- 项目统一使用 `ITOPS_*` 命名的环境变量约定，覆盖：
  - MySQL
  - RabbitMQ
  - Redis
  - Qdrant
  - Embedding 模型
  - Chat 模型
- Python 侧不直接解析 Spring YAML，而是按以下顺序读取同名配置：
  1. 仓库根目录 `.env`
  2. 仓库根目录 `.env.local`
  3. `agent_runtime/.env`
  4. 系统环境变量最终覆盖
- Python 依赖清单维护在 `agent_runtime/requirements.txt`
- Python 本地配置示例维护在项目根 `.env.example`

## 编码偏好

- 实体类优先使用 `Lombok` 降低模板代码噪音。
- 控制层保持轻量，业务逻辑统一下沉到 `service/impl/`。
- 状态流转、并发保护、审计持久化等核心逻辑统一放在服务层实现。
- 尽可能编写代码注释，重点说明“为什么这样做”，不要写成语法翻译。
- 新增后端模块时优先沿用现有分层结构，不随意发散目录层级。

## 技术栈约束

### Java 侧

- Java Web 框架统一使用 `Spring Boot`。
- 持久层统一使用 `MyBatis-Plus`，不引入 `Spring Data JPA`、`Hibernate`。
- 数据库统一使用 `MySQL`，不使用 `H2`。
- 数据库结构通过项目内显式 SQL 初始化脚本维护，不引入 `Flyway`。
- 并发更新统一基于 `MyBatis-Plus` 乐观锁能力处理。

### Python 侧

- 结构化数据校验统一使用 `Pydantic`。
- 测试统一使用 `pytest`。
- Agent Runtime 的节点输出契约、上下文对象与测试示例都应围绕 `Pydantic + pytest` 组织。
- Agent 工作流统一使用 `LangGraph StateGraph`，未安装时回退到顺序执行器。
- LLM Client 通过 `ITOPS_CHAT_PROVIDER` 配置化切换（mock/deepseek/qwen/glm），真实供应商走 OpenAI 兼容协议。

## 当前统一技术栈

- Java 21
- Spring Boot 3
- Spring Web MVC
- MyBatis-Plus
- MySQL
- Lombok
- Maven
- JUnit 5
- Python 3
- Pydantic
- pytest
- LangGraph（Agent 状态图编排）
- OpenAI SDK（DeepSeek/Qwen/GLM 通过 OpenAI 兼容协议接入）

## Git 约束

- 涉及 Git 操作时，提交信息统一使用中文。
- 提交时按模块拆分，避免把无关改动揉在一起。
- `git commit message` 中不要加入 `Co-Authored-By`。

## Python 运行环境

- 运行 Python 代码时使用 `D:/anaconda3/envs/lc/python.exe`。
- 不使用 `conda run` 方式执行项目内 Python 命令。

## 开发流程

- 每次开发代码前先参考`docs\itops_agent_codex_task_pack\prompts\CODEX_MASTER_PROMPT.md`
- Phase 3 之后如涉及 Candidate Plan、Tool Registry、Harness Decision 等契约，优先同步检查 `docs\itops_agent_codex_task_pack\contracts\`
- 做收尾同步时，优先更新 `README.md`、`agent_runtime/README.md` 与本文件，避免项目说明停留在旧 Phase
