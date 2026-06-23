"""缺失槽位追问节点。"""

from __future__ import annotations

from agent_runtime.llm_client.base import LLMRequest
from agent_runtime.models import QuestionResult


def run(client, context: dict) -> dict:
    payload = client.invoke(LLMRequest(node_name="generate_question", context=context))
    return QuestionResult.model_validate(payload).model_dump()
