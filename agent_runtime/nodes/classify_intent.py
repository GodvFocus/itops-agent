"""意图分类节点。"""

from __future__ import annotations

from agent_runtime.llm_client.base import LLMRequest
from agent_runtime.models import IntentResult


def run(client, context: dict) -> dict:
    payload = client.invoke(LLMRequest(node_name="classify_intent", context=context))
    return IntentResult.model_validate(payload).model_dump()
