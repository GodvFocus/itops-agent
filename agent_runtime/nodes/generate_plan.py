"""根据命中的 SOP 生成候选 Plan，但不会直接执行任何工具。"""

from __future__ import annotations

from agent_runtime.plan_generator import CandidatePlanGenerator


def run(state: dict) -> dict:
    selected_sop_id = state["selectedSopId"]
    plan = CandidatePlanGenerator().generate(state, selected_sop_id)
    return plan.model_dump()
