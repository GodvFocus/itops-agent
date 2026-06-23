# Agent Runtime

这是 `Phase 2` 的 Python Agent Runtime 骨架，用于固化后续真实 LLM / LangGraph 接入时要遵守的节点边界和结构化输出契约。

当前实现特点：

- 提供 `LLM Client` 抽象与 `MockLLMClient`
- 提供 `classify_intent`、`extract_slots`、`generate_question` 三个节点
- 提供 `ContextBuilder`
- 优先尝试构建 LangGraph 工作流；本地未安装 `langgraph` 时回退到顺序执行器
- 所有节点输出统一通过 `Pydantic` 模型校验，再交给上层使用

当前目录不会直接调用 Java Harness，也不会执行任何工具操作。
