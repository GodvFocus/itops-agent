# Agent Runtime

这是 `Phase 3` 的 Python Agent Runtime，用于把工单理解结果升级为“可校验但不执行”的 Candidate Plan。

当前实现特点：

- 提供 `LLM Client` 抽象与 `MockLLMClient`
- 提供 `classify_intent`、`extract_slots`、`generate_question`、`retrieve_sop`、`generate_plan` 五个节点
- 提供 10 条结构化 SOP seed 数据
- 提供 Tool Registry 读取、Qdrant 兼容向量入库与检索能力
- 提供 Candidate Plan 的 `Pydantic` / 合同级基础校验
- 优先尝试构建 LangGraph 工作流；本地未安装 `langgraph` 时回退到顺序执行器

当前目录仍不会直接执行任何工具操作，生成的 Plan 只用于交给 Java Harness 做后续放行判断。
