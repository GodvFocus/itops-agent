"""LLM Client 共享的槽位解析与文本构建工具。

Mock LLM 和真实 LLM 都需要这些逻辑，因此抽取到共享模块避免重复维护：
- 按意图确定必填槽位
- 计算缺失槽位
- 从上下文构建供模型理解的文本
- 槽位中文标签（用于追问生成）
"""

from __future__ import annotations

from typing import Any

# 各意图对应的必填槽位，与 SOP catalog 的 required_slots 保持一致。
REQUIRED_SLOTS_BY_INTENT: dict[str, list[str]] = {
    "ACCOUNT_LOGIN_ISSUE": ["employeeId", "targetSystem"],
    "VPN_CONNECTION_ISSUE": ["employeeId", "deviceType", "errorMessage"],
    "PERMISSION_REQUEST": ["employeeId", "targetSystem", "permissionLevel", "reason", "duration"],
    "UNKNOWN": [],
}

# 槽位中文名，用于生成用户可读的追问文案。
SLOT_LABELS: dict[str, str] = {
    "employeeId": "员工编号",
    "targetSystem": "目标系统",
    "deviceType": "设备类型",
    "errorMessage": "报错信息",
    "permissionLevel": "权限级别",
    "reason": "申请原因",
    "duration": "申请时长",
}


def resolve_missing(intent: str, slots: dict[str, Any]) -> list[str]:
    """根据意图和已抽取的槽位，计算仍缺失的必填槽位。"""
    required = REQUIRED_SLOTS_BY_INTENT.get(intent, [])
    return [slot for slot in required if not slots.get(slot)]


def build_text(context: dict[str, Any]) -> str:
    """从上下文中提取工单标题、描述和最近消息，拼接成供模型理解的文本。"""
    parts: list[str] = []
    ticket_facts = context.get("ticket_facts", {})
    parts.append(str(ticket_facts.get("title", "")))
    parts.append(str(ticket_facts.get("description", "")))
    for message in context.get("recent_messages", []):
        parts.append(str(message.get("content", "")))
    return "\n".join(parts)
