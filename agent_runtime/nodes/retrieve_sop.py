"""检索最匹配的 SOP，供后续 Candidate Plan 生成使用。"""

from __future__ import annotations

from agent_runtime.retrieval import get_default_retriever


def run(state: dict) -> dict:
    result = get_default_retriever().retrieve(state)
    return result.model_dump()
