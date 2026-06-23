"""使用 Pydantic 固化 Agent 节点输出契约。"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict


class IntentResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    intent: str
    confidence: float
    reasoning: str


class SlotResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    slots: dict[str, Any]
    missingSlots: list[str]
    reasoning: str


class QuestionResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    shouldAskUser: bool
    question: str
    nextStep: str
