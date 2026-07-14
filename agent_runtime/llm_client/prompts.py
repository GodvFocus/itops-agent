"""各节点的系统提示词与用户提示词构建。

提示词设计原则：
- 明确约束输出为 JSON，字段名与 Pydantic 模型完全一致。
- 给出意图枚举和槽位清单，避免模型自由发挥。
- 提供少量示例，稳定输出格式。
- 中文提示词，与工单场景一致。
"""

from __future__ import annotations

from typing import Any

from agent_runtime.llm_client.slot_utils import SLOT_LABELS, build_text

# ---------------------------------------------------------------------------
# classify_intent
# ---------------------------------------------------------------------------

CLASSIFY_INTENT_SYSTEM = """\
你是企业 IT 服务台意图分类助手。请根据用户工单内容，判断属于以下哪类意图：

- ACCOUNT_LOGIN_ISSUE：账号登录异常（账号锁定、密码错误、无法登录、邮箱登录失败等）
- VPN_CONNECTION_ISSUE：VPN 连接问题（VPN 认证失败、VPN 权限缺失、MFA 换绑等）
- PERMISSION_REQUEST：权限申请（Jira/GitLab/生产系统等权限开通、管理员权限申请等）
- UNKNOWN：不在以上三类范围内的问题

请严格以 JSON 格式返回，包含以下字段：
{
  "intent": "意图标签（必须是 ACCOUNT_LOGIN_ISSUE、VPN_CONNECTION_ISSUE、PERMISSION_REQUEST、UNKNOWN 之一）",
  "confidence": "置信度，0 到 1 之间的浮点数",
  "reasoning": "分类理由，简短说明命中了哪些关键词或语义线索"
}
"""


# ---------------------------------------------------------------------------
# extract_slots
# ---------------------------------------------------------------------------

EXTRACT_SLOTS_SYSTEM = """\
你是企业 IT 服务台信息抽取助手。请从用户工单内容中抽取以下槽位信息：

- employeeId：员工编号（如 E10086、U8801 等字母+数字组合）
- targetSystem：目标系统（如 OA、ERP、CRM、JIRA、GITLAB、EMAIL、数据库等）
- deviceType：设备类型（IOS、ANDROID、MOBILE、WINDOWS、MAC）
- errorMessage：报错信息（如"账号已锁定"、"认证失败"、"access denied"、"登录失败"等）
- permissionLevel：权限级别（ADMIN、READ_WRITE、READ_ONLY）
- reason：申请原因
- duration：申请时长（如"3天"、"1周"、"2个月"等）
- mfaRecentlyChanged：是否最近更换过 MFA 设备或换绑验证器（true/false）

规则：
1. 只抽取工单中明确提及或可合理推断的信息，不要凭空猜测。
2. 不存在的槽位不要出现在结果中。
3. 请严格以 JSON 格式返回，包含以下字段：
{
  "slots": "抽取到的槽位键值对对象",
  "reasoning": "抽取说明，简述抽取了哪些槽位及其依据"
}

示例输入：我的 OA 账号被锁定了，工号是 E10086
示例输出：
{
  "slots": {"employeeId": "E10086", "targetSystem": "OA", "errorMessage": "账号已锁定"},
  "reasoning": "从文本中抽取了员工编号、目标系统和报错信息"
}
"""


# ---------------------------------------------------------------------------
# generate_question
# ---------------------------------------------------------------------------

def build_question_user_prompt(context: dict[str, Any]) -> str:
    """为追问节点构建用户提示词，包含意图和缺失槽位信息。"""
    intent = context.get("intent", "UNKNOWN")
    missing_slots = context.get("missingSlots", [])
    ticket_text = build_text(context)

    slot_desc = "、".join(SLOT_LABELS.get(slot, slot) for slot in missing_slots) if missing_slots else "无"
    return (
        f"工单内容：\n{ticket_text}\n\n"
        f"当前识别意图：{intent}\n"
        f"缺失槽位：{slot_desc}\n\n"
        f"请生成一条简短、友好的中文追问，引导用户补充缺失的信息。"
        f"直接输出追问文本，不要加引号或其他格式。"
    )
