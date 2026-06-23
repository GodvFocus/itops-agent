"""维护 Phase 3 所需的结构化 SOP seed 数据。"""

from __future__ import annotations

from functools import lru_cache

from agent_runtime.models import SopMetadata, SopStepBlueprint


@lru_cache(maxsize=1)
def get_seed_sops() -> tuple[SopMetadata, ...]:
    return (
        SopMetadata(
            sop_id="SOP-ACC-LOCKED-001",
            name="账号锁定处理 SOP",
            intent="ACCOUNT_LOGIN_ISSUE",
            required_slots=["employeeId", "targetSystem"],
            applicable_conditions=["账号已锁定", "locked", "连续输错密码", "目录状态显示 locked"],
            risk_level="LOW",
            allowed_tools=[
                "AccountTool.queryAccountStatus",
                "AccountTool.unlockAccount",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="AccountTool", action="queryAccountStatus", reason="先确认账号确实处于锁定状态，避免误操作。"),
                SopStepBlueprint(tool="AccountTool", action="unlockAccount", reason="锁定场景可以直接生成解锁候选动作，不等待额外审批。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="需要把候选处理结果同步给报障员工，减少重复追问。"),
            ],
            approval_required_steps=[],
            escalation_rules=["如果账号并未锁定而是目录异常，需要转人工排查认证链路。"],
        ),
        SopMetadata(
            sop_id="SOP-ACC-LOGIN-ABNORMAL-001",
            name="登录异常处理 SOP",
            intent="ACCOUNT_LOGIN_ISSUE",
            required_slots=["employeeId", "targetSystem"],
            applicable_conditions=["invalid credentials", "密码错误", "登录失败", "认证异常", "sign in failed"],
            risk_level="LOW",
            allowed_tools=[
                "AccountTool.queryAccountStatus",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="AccountTool", action="queryAccountStatus", reason="先核实账号是否正常，区分密码错误和锁定场景。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="当前没有密码重置工具，候选计划要明确告知下一步人工处置建议。"),
            ],
            approval_required_steps=[],
            escalation_rules=["若账号状态正常但仍无法登录，需要转人工排查密码、SSO 或目录同步问题。"],
        ),
        SopMetadata(
            sop_id="SOP-VPN-AUTH-FAIL-001",
            name="VPN 认证失败 SOP",
            intent="VPN_CONNECTION_ISSUE",
            required_slots=["employeeId", "deviceType", "errorMessage"],
            applicable_conditions=["VPN 认证失败", "vpn auth failed", "forticlient 认证失败", "无法通过 VPN 认证"],
            risk_level="LOW",
            allowed_tools=[
                "VpnTool.queryVpnLoginFailure",
                "MfaTool.queryMfaStatus",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="VpnTool", action="queryVpnLoginFailure", reason="先确认失败原因来自账号、网络还是设备端。"),
                SopStepBlueprint(tool="MfaTool", action="queryMfaStatus", reason="VPN 认证失败常与 MFA 状态耦合，需要同时检查。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="候选计划需要把排查路径同步给用户和后续处理人。"),
            ],
            approval_required_steps=[],
            escalation_rules=["若连续失败且工具查询无异常，升级到网络或终端团队继续排查。"],
        ),
        SopMetadata(
            sop_id="SOP-VPN-PERM-MISSING-001",
            name="VPN 权限缺失 SOP",
            intent="VPN_CONNECTION_ISSUE",
            required_slots=["employeeId", "errorMessage"],
            applicable_conditions=["VPN 未开通", "VPN 无权限", "access denied", "未分配 VPN 权限"],
            risk_level="MEDIUM",
            allowed_tools=[
                "VpnTool.queryVpnPermission",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="VpnTool", action="queryVpnPermission", reason="先核实 VPN 权限现状，避免把认证问题误当成授权问题。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="当前注册工具里没有 VPN 授权写操作，需明确转人工或线下审批。"),
            ],
            approval_required_steps=[],
            escalation_rules=["若确认缺少 VPN 权限，则转给对应权限管理员处理。"],
        ),
        SopMetadata(
            sop_id="SOP-MFA-DEVICE-CHANGE-001",
            name="MFA 设备更换 SOP",
            intent="VPN_CONNECTION_ISSUE",
            required_slots=["employeeId", "deviceType"],
            applicable_conditions=["更换手机", "换绑 MFA", "新设备", "重新绑定验证器", "刚换手机"],
            risk_level="HIGH",
            allowed_tools=[
                "MfaTool.queryMfaStatus",
                "MfaTool.resetMfaBindingRequest",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="MfaTool", action="queryMfaStatus", reason="先确认当前 MFA 绑定状态，避免重复发起重置申请。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="高风险动作前要先通知申请人和审批链路。"),
            ],
            approval_required_steps=[
                SopStepBlueprint(tool="MfaTool", action="resetMfaBindingRequest", reason="MFA 换绑属于高风险身份动作，必须进入审批而不是直接执行。", requiredApproval=True),
            ],
            escalation_rules=["若用户无法完成身份核验，则升级到服务台人工核验。"],
        ),
        SopMetadata(
            sop_id="SOP-PERM-JIRA-STANDARD-001",
            name="Jira 普通权限申请 SOP",
            intent="PERMISSION_REQUEST",
            required_slots=["employeeId", "targetSystem", "permissionLevel", "reason", "duration"],
            applicable_conditions=["Jira", "JIRA", "普通权限", "只读权限", "写入权限"],
            risk_level="MEDIUM",
            allowed_tools=[
                "PermissionTool.queryPermission",
                "PermissionTool.grantPermission",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="PermissionTool", action="queryPermission", reason="授予前先查当前权限，避免重复开通。"),
                SopStepBlueprint(tool="PermissionTool", action="grantPermission", reason="普通 Jira 权限可生成可执行候选动作，供 Harness 继续校验。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="需要把申请结果和时限同步给申请人。"),
            ],
            approval_required_steps=[],
            escalation_rules=["若申请的是 Jira 管理员权限，则切换到高风险审批 SOP。"],
        ),
        SopMetadata(
            sop_id="SOP-PERM-GITLAB-STANDARD-001",
            name="GitLab 普通权限申请 SOP",
            intent="PERMISSION_REQUEST",
            required_slots=["employeeId", "targetSystem", "permissionLevel", "reason", "duration"],
            applicable_conditions=["GitLab", "GITLAB", "普通权限", "代码仓库权限", "提交权限"],
            risk_level="MEDIUM",
            allowed_tools=[
                "PermissionTool.queryPermission",
                "PermissionTool.grantPermission",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="PermissionTool", action="queryPermission", reason="先查现有仓库权限，避免多次授予同级权限。"),
                SopStepBlueprint(tool="PermissionTool", action="grantPermission", reason="普通 GitLab 权限可以形成标准候选动作，等待 Harness 判断是否放行。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="申请人需要明确获知权限级别和有效时长。"),
            ],
            approval_required_steps=[],
            escalation_rules=["若涉及受保护仓库或管理员角色，则升级到高风险审批 SOP。"],
        ),
        SopMetadata(
            sop_id="SOP-PERM-PROD-ADMIN-001",
            name="生产系统管理员权限申请 SOP",
            intent="PERMISSION_REQUEST",
            required_slots=["employeeId", "targetSystem", "permissionLevel", "reason", "duration"],
            applicable_conditions=["生产系统", "生产环境", "管理员权限", "上线", "变更窗口"],
            risk_level="HIGH",
            allowed_tools=[
                "PermissionTool.queryPermission",
                "PermissionTool.grantPermission",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="PermissionTool", action="queryPermission", reason="高风险授权前必须先确认当前权限基线。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="需要先通知审批人与申请人，避免绕过高风险控制。"),
            ],
            approval_required_steps=[
                SopStepBlueprint(tool="PermissionTool", action="grantPermission", reason="生产系统管理员权限必须带审批标记，绝不能直接放行。", requiredApproval=True),
            ],
            escalation_rules=["如果申请理由无法证明生产变更需要，必须拒绝并转人工复核。"],
        ),
        SopMetadata(
            sop_id="SOP-EMAIL-LOGIN-MANUAL-001",
            name="邮箱无法登录 SOP",
            intent="ACCOUNT_LOGIN_ISSUE",
            required_slots=["employeeId"],
            applicable_conditions=["邮箱无法登录", "EMAIL 登录失败", "邮件系统异常", "exchange login failed"],
            risk_level="MEDIUM",
            allowed_tools=[
                "AccountTool.queryAccountStatus",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="AccountTool", action="queryAccountStatus", reason="先收集基础账号状态，帮助人工团队缩小排查范围。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="邮箱登录涉及链路较多，当前阶段建议直接转人工并同步用户。"),
            ],
            approval_required_steps=[],
            escalation_rules=["邮件系统登录问题默认转人工，不在当前自动化执行范围内。"],
        ),
        SopMetadata(
            sop_id="SOP-PERM-HIGH-RISK-APPROVAL-001",
            name="高风险权限审批 SOP",
            intent="PERMISSION_REQUEST",
            required_slots=["employeeId", "targetSystem", "permissionLevel", "reason", "duration"],
            applicable_conditions=["高风险权限", "管理员权限", "生产系统", "敏感系统", "ADMIN"],
            risk_level="HIGH",
            allowed_tools=[
                "PermissionTool.queryPermission",
                "PermissionTool.grantPermission",
                "NotificationTool.sendNotification",
            ],
            auto_executable_steps=[
                SopStepBlueprint(tool="PermissionTool", action="queryPermission", reason="审批前先确认当前权限和申请差异，避免无效审批。"),
                SopStepBlueprint(tool="NotificationTool", action="sendNotification", reason="审批链上的相关人必须提前收到候选计划。"),
            ],
            approval_required_steps=[
                SopStepBlueprint(tool="PermissionTool", action="grantPermission", reason="任何高风险授权动作都必须保留审批门禁。", requiredApproval=True),
            ],
            escalation_rules=["如果缺少审批依据或申请时长异常，直接转人工审查。"],
        ),
    )


def get_sop_by_id(sop_id: str) -> SopMetadata:
    for sop in get_seed_sops():
        if sop.sop_id == sop_id:
            return sop
    raise KeyError(f"Unknown SOP: {sop_id}")
