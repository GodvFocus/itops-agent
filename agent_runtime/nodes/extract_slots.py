"""槽位抽取节点。"""

from __future__ import annotations

from agent_runtime.llm_client.base import LLMRequest
from agent_runtime.models import SlotResult


def run(client, context: dict) -> dict:
    payload = client.invoke(LLMRequest(node_name="extract_slots", context=context))
    return SlotResult.model_validate(payload).model_dump()
