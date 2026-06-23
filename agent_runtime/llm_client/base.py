"""LLM Client 抽象，后续可替换为真实模型实现。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


@dataclass(slots=True)
class LLMRequest:
    node_name: str
    context: dict


class BaseLLMClient(Protocol):
    def invoke(self, request: LLMRequest) -> dict: ...
