"""读取 Tool Registry 合同，确保 Plan 只使用已注册工具。"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from agent_runtime.models import ToolRegistryEntry

DEFAULT_TOOL_REGISTRY_PATH = Path("docs/itops_agent_codex_task_pack/contracts/tool_registry.yaml")


def _parse_scalar(raw_value: str):
    value = raw_value.strip()
    if not value:
        return ""
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1]
    if value.startswith("'") and value.endswith("'"):
        return value[1:-1]
    if value.startswith("[") and value.endswith("]"):
        inner = value[1:-1].strip()
        if not inner:
            return []
        return [item.strip().strip('"').strip("'") for item in inner.split(",")]
    if value.lower() == "true":
        return True
    if value.lower() == "false":
        return False
    return value


@lru_cache(maxsize=4)
def load_tool_registry(path: str | Path = DEFAULT_TOOL_REGISTRY_PATH) -> dict[tuple[str, str], ToolRegistryEntry]:
    contract_path = Path(path)
    current: dict[str, object] = {}
    entries: list[ToolRegistryEntry] = []

    for raw_line in contract_path.read_text(encoding="utf-8").splitlines():
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#") or stripped == "tools:":
            continue
        if stripped.startswith("- "):
            if current:
                entries.append(ToolRegistryEntry.model_validate(current))
            current = {}
            stripped = stripped[2:].strip()
        if ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        current[key.strip()] = _parse_scalar(value)

    if current:
        entries.append(ToolRegistryEntry.model_validate(current))

    return {(entry.tool, entry.action): entry for entry in entries}


def resolve_tool_entry(tool: str, action: str) -> ToolRegistryEntry | None:
    return load_tool_registry().get((tool, action))


def is_registered_tool(tool: str, action: str) -> bool:
    return resolve_tool_entry(tool, action) is not None
