const createForm = document.getElementById("create-form");
const createResultEl = document.getElementById("create-result");
const ticketListEl = document.getElementById("ticket-list");
const refreshButton = document.getElementById("refresh-list");
const ticketEmptyEl = document.getElementById("ticket-empty");
const ticketViewEl = document.getElementById("ticket-view");
const ticketTitleEl = document.getElementById("ticket-title");
const ticketStatusBadgeEl = document.getElementById("ticket-status-badge");
const ticketSummaryEl = document.getElementById("ticket-summary");
const resolutionSummaryEl = document.getElementById("resolution-summary");
const planPanelEl = document.getElementById("plan-panel");
const approvalPanelEl = document.getElementById("approval-panel");
const conversationPanelEl = document.getElementById("conversation-panel");
const timelinePanelEl = document.getElementById("timeline-panel");
const toolsPanelEl = document.getElementById("tools-panel");
const historyPanelEl = document.getElementById("history-panel");
const actionsPanelEl = document.getElementById("actions-panel");
const messageForm = document.getElementById("message-form");
const messageResultEl = document.getElementById("message-result");
const actionResultEl = document.getElementById("action-result");

const demoCases = {
    account: {
        title: "OA 登录失败",
        description: "我登录不上 OA 了，提示账号被锁定，我的工号是 E10086。"
    },
    vpn: {
        title: "VPN 无法连接",
        description: "VPN 连不上，提示认证失败，昨天换过手机。"
    },
    permission: {
        title: "生产数据库管理员权限申请",
        description: "我需要生产数据库管理员权限，原因是用于线上排障，时长两天。我的工号是 E10086。"
    }
};

let selectedTicketId = null;
let selectedTicket = null;
let selectedTimeline = null;
let pollHandle = null;

async function api(path, options = {}) {
    const response = await fetch(path, {
        headers: { "Content-Type": "application/json" },
        ...options
    });
    if (!response.ok) {
        const error = await response.json().catch(() => ({ message: "Request failed" }));
        throw new Error(error.message || "Request failed");
    }
    if (response.status === 204) {
        return null;
    }
    return response.json();
}

function setMessage(element, message, isError = false) {
    element.textContent = message;
    element.className = isError ? "feedback error" : "feedback ok";
}

function clearMessage(element) {
    element.textContent = "";
    element.className = "feedback muted";
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function formatTime(value) {
    if (!value) {
        return "-";
    }
    return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function formatObject(value) {
    if (value == null) {
        return "-";
    }
    if (typeof value === "string") {
        return value;
    }
    return JSON.stringify(value, null, 2);
}

function statusTone(status) {
    if (["CLOSED", "RESOLVED", "WAITING_USER_CONFIRM"].includes(status)) {
        return "good";
    }
    if (["WAITING_APPROVAL", "NEED_MORE_INFO", "PLAN_VALIDATING"].includes(status)) {
        return "warn";
    }
    if (["ESCALATED", "FAILED", "MANUAL_TAKEOVER"].includes(status)) {
        return "bad";
    }
    return "neutral";
}

function laneTone(lane) {
    if (lane === "AGENT") {
        return "lane-agent";
    }
    if (lane === "APPROVAL") {
        return "lane-approval";
    }
    return "lane-tool";
}

function startPollingIfNeeded() {
    stopPolling();
    if (!selectedTicket || !["WAITING_APPROVAL", "EXECUTING", "WAITING_USER_CONFIRM"].includes(selectedTicket.status)) {
        return;
    }
    pollHandle = window.setInterval(() => {
        if (selectedTicketId) {
            loadTicketWorkspace(selectedTicketId, true).catch((error) => setMessage(actionResultEl, error.message, true));
        }
    }, 2500);
}

function stopPolling() {
    if (pollHandle) {
        window.clearInterval(pollHandle);
        pollHandle = null;
    }
}

async function loadTickets() {
    const tickets = await api("/api/tickets");
    ticketListEl.innerHTML = "";
    if (tickets.length === 0) {
        ticketListEl.innerHTML = '<p class="muted">还没有工单，先创建一条试试。</p>';
        return;
    }
    tickets.forEach((ticket) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `ticket-card ${ticket.ticketId === selectedTicketId ? "active" : ""}`;
        button.innerHTML = `
            <div class="ticket-card-top">
                <strong>${escapeHtml(ticket.title)}</strong>
                <span class="mini-pill ${statusTone(ticket.status)}">${escapeHtml(ticket.status)}</span>
            </div>
            <div class="ticket-meta">${escapeHtml(ticket.ticketId)}</div>
            <div class="ticket-meta">优先级 ${escapeHtml(ticket.priority)} · 更新时间 ${formatTime(ticket.updatedAt)}</div>
        `;
        button.addEventListener("click", () => loadTicketWorkspace(ticket.ticketId));
        ticketListEl.appendChild(button);
    });
}

async function loadTicketWorkspace(ticketId, silent = false) {
    const [ticket, timeline] = await Promise.all([
        api(`/api/tickets/${ticketId}`),
        api(`/api/tickets/${ticketId}/timeline`)
    ]);
    selectedTicketId = ticketId;
    selectedTicket = ticket;
    selectedTimeline = timeline;
    renderWorkspace();
    if (!silent) {
        clearMessage(messageResultEl);
        clearMessage(actionResultEl);
    }
    await loadTickets();
    startPollingIfNeeded();
}

function renderWorkspace() {
    if (!selectedTicket || !selectedTimeline) {
        ticketEmptyEl.classList.remove("hidden");
        ticketViewEl.classList.add("hidden");
        return;
    }
    ticketEmptyEl.classList.add("hidden");
    ticketViewEl.classList.remove("hidden");

    ticketTitleEl.textContent = `${selectedTicket.title} · ${selectedTicket.ticketId}`;
    ticketStatusBadgeEl.textContent = selectedTicket.status;
    ticketStatusBadgeEl.className = `status-pill ${statusTone(selectedTicket.status)}`;
    resolutionSummaryEl.textContent = selectedTimeline.resolutionSummary || "暂无处理摘要";

    renderSummary();
    renderPlan();
    renderApprovals();
    renderConversation();
    renderTimeline();
    renderToolCalls();
    renderStatusHistory();
    renderActions();
}

function renderSummary() {
    const context = selectedTicket.ticketContext;
    const slotEntries = Object.entries(context.slots || {});
    const slotHtml = slotEntries.length === 0
        ? '<span class="empty-inline">暂无已确认槽位</span>'
        : slotEntries.map(([key, value]) => `
            <span class="chip"><span>${escapeHtml(key)}</span><strong>${escapeHtml(value)}</strong></span>
        `).join("");
    const missingHtml = (context.missingSlots || []).length === 0
        ? '<span class="mini-pill good">已满足当前阶段要求</span>'
        : context.missingSlots.map((slot) => `<span class="mini-pill warn">${escapeHtml(slot)}</span>`).join("");

    ticketSummaryEl.innerHTML = `
        <div class="summary-card">
            <span>当前状态</span>
            <strong>${escapeHtml(selectedTicket.status)}</strong>
            <small>风险 ${escapeHtml(selectedTicket.riskLevel)}</small>
        </div>
        <div class="summary-card">
            <span>识别意图</span>
            <strong>${escapeHtml(selectedTicket.intent)}</strong>
            <small>最近节点 ${escapeHtml(context.lastAgentStep)}</small>
        </div>
        <div class="summary-card">
            <span>创建人</span>
            <strong>${escapeHtml(selectedTicket.creatorId)}</strong>
            <small>${escapeHtml(selectedTicket.creatorRole)}</small>
        </div>
        <div class="summary-card">
            <span>上下文更新时间</span>
            <strong>${formatTime(context.updatedAt)}</strong>
            <small>版本 ${escapeHtml(selectedTicket.version)}</small>
        </div>
        <div class="summary-card wide">
            <span>已知槽位</span>
            <div class="chip-row">${slotHtml}</div>
        </div>
        <div class="summary-card wide">
            <span>缺失槽位</span>
            <div class="chip-row">${missingHtml}</div>
        </div>
    `;
}

function renderPlan() {
    const currentPlan = selectedTimeline.currentPlan || {};
    const steps = currentPlan.steps || [];
    if (steps.length === 0) {
        planPanelEl.innerHTML = '<div class="stack-card"><strong>暂无 Candidate Plan</strong><p>当前工单还没进入计划阶段，或计划尚未生成。</p></div>';
        return;
    }
    const matched = (selectedTimeline.matchedSopIds || []).map((item) => `<span class="mini-pill neutral">${escapeHtml(item)}</span>`).join("");
    const stepsHtml = steps.map((step) => `
        <div class="step-card ${step.requiredApproval ? "approval-step" : ""}">
            <div class="step-head">
                <strong>Step ${escapeHtml(step.stepNo)}</strong>
                <span class="mini-pill ${step.requiredApproval ? "warn" : "neutral"}">${escapeHtml(step.actionType)}</span>
            </div>
            <div>${escapeHtml(step.tool)}.${escapeHtml(step.action)}</div>
            <p>${escapeHtml(step.reason)}</p>
            <pre>${escapeHtml(formatObject(step.params))}</pre>
        </div>
    `).join("");
    planPanelEl.innerHTML = `
        <div class="stack-card">
            <strong>${escapeHtml(currentPlan.selectedSopId || "-")}</strong>
            <p>${escapeHtml(currentPlan.goal || "基于当前工单生成的结构化计划")}</p>
            <div class="chip-row">${matched || '<span class="empty-inline">暂无命中 SOP</span>'}</div>
        </div>
        ${stepsHtml}
    `;
}

function renderApprovals() {
    const approvals = selectedTimeline.approvalTasks || [];
    if (approvals.length === 0) {
        approvalPanelEl.innerHTML = '<div class="stack-card"><strong>当前无审批任务</strong><p>低风险自动执行场景不会停在审批门禁。</p></div>';
        return;
    }
    approvalPanelEl.innerHTML = approvals.map((task) => `
        <div class="stack-card">
            <div class="step-head">
                <strong>${escapeHtml(task.approvalId)}</strong>
                <span class="mini-pill ${statusTone(task.status)}">${escapeHtml(task.status)}</span>
            </div>
            <p>${escapeHtml(task.requestedReason)}</p>
            <div class="meta-line">审批类型：${escapeHtml(task.approvalType)} · 计划：${escapeHtml(task.planId)}</div>
            <div class="meta-line">创建时间：${formatTime(task.createdAt)}</div>
            <div class="meta-line">审批人：${escapeHtml(task.approverId || "-")} · 备注：${escapeHtml(task.approverComment || "-")}</div>
        </div>
    `).join("");
}

function renderConversation() {
    conversationPanelEl.innerHTML = selectedTicket.conversationMessages.map((message) => `
        <article class="message ${message.role === "AGENT" ? "agent" : "user"}">
            <div class="message-top">
                <strong>${escapeHtml(message.role)}</strong>
                <span>${escapeHtml(message.messageType)}</span>
            </div>
            <p>${escapeHtml(message.content)}</p>
            <small>${formatTime(message.createdAt)}</small>
        </article>
    `).join("");

    if (selectedTicket.status === "NEED_MORE_INFO") {
        messageForm.classList.remove("hidden");
    } else {
        messageForm.classList.add("hidden");
    }
}

function renderTimeline() {
    const events = selectedTimeline.timelineEvents || [];
    timelinePanelEl.innerHTML = events.length === 0
        ? '<div class="stack-card"><strong>还没有时间线事件</strong><p>创建工单后，Agent / Approval / Tool 的事件会陆续出现。</p></div>'
        : events.map((event) => `
            <div class="timeline-item ${laneTone(event.lane)}">
                <div class="timeline-marker">${escapeHtml(event.lane)}</div>
                <div class="timeline-body">
                    <div class="step-head">
                        <strong>${escapeHtml(event.title)}</strong>
                        <span class="mini-pill ${statusTone(event.status)}">${escapeHtml(event.status)}</span>
                    </div>
                    <p>${escapeHtml(event.detail)}</p>
                    <small>${formatTime(event.createdAt)}</small>
                </div>
            </div>
        `).join("");
}

function renderToolCalls() {
    const toolCalls = selectedTimeline.toolCalls || [];
    toolsPanelEl.innerHTML = toolCalls.length === 0
        ? '<div class="stack-card"><strong>暂无工具执行记录</strong><p>如果当前工单还在追问或审批，工具日志会晚一点出现。</p></div>'
        : toolCalls.map((call) => `
            <div class="stack-card">
                <div class="step-head">
                    <strong>Step ${escapeHtml(call.stepNo)} · ${escapeHtml(call.toolName)}.${escapeHtml(call.actionName)}</strong>
                    <span class="mini-pill ${statusTone(call.status)}">${escapeHtml(call.status)}</span>
                </div>
                <div class="meta-line">Decision：${escapeHtml(call.decision)} · Attempt：${escapeHtml(call.attemptNo)}</div>
                <pre>Request
${escapeHtml(formatObject(call.request))}</pre>
                <pre>Response
${escapeHtml(formatObject(call.response))}</pre>
                <div class="meta-line">${call.errorMessage ? `错误：${escapeHtml(call.errorMessage)}` : `时间：${formatTime(call.createdAt)}`}</div>
            </div>
        `).join("");
}

function renderStatusHistory() {
    historyPanelEl.innerHTML = selectedTicket.statusHistory.map((item) => `
        <div class="stack-card">
            <strong>${escapeHtml(item.fromStatus || "NONE")} -> ${escapeHtml(item.toStatus)}</strong>
            <div class="meta-line">${escapeHtml(item.actorId)} (${escapeHtml(item.actorRole)})</div>
            <p>${escapeHtml(item.comment || "无备注")}</p>
            <small>${formatTime(item.createdAt)}</small>
        </div>
    `).join("");
}

function renderActions() {
    const approvals = selectedTimeline.approvalTasks || [];
    const pendingApproval = approvals.find((item) => item.status === "PENDING");

    const approvalHtml = pendingApproval
        ? `
            <div class="action-card">
                <h3>审批操作</h3>
                <label>
                    审批人 ID
                    <input id="approver-id" value="AP2001" placeholder="AP2001">
                </label>
                <label>
                    审批备注
                    <textarea id="approver-comment" rows="3" placeholder="可选：补充审批原因"></textarea>
                </label>
                <div class="button-row">
                    <button type="button" data-action="approve" data-approval-id="${escapeHtml(pendingApproval.approvalId)}">审批通过</button>
                    <button type="button" class="ghost-btn" data-action="reject" data-approval-id="${escapeHtml(pendingApproval.approvalId)}">审批拒绝</button>
                </div>
            </div>
        `
        : `
            <div class="action-card muted-card">
                <h3>审批操作</h3>
                <p>当前没有待处理审批。</p>
            </div>
        `;

    const confirmHtml = selectedTicket.status === "WAITING_USER_CONFIRM"
        ? `
            <div class="action-card">
                <h3>用户确认</h3>
                <label>
                    用户备注
                    <textarea id="confirm-comment" rows="3" placeholder="例如：已经可以登录 / 仍未恢复"></textarea>
                </label>
                <div class="button-row">
                    <button type="button" data-action="confirm-resolved">已解决，关闭工单</button>
                    <button type="button" class="ghost-btn" data-action="confirm-unresolved">未解决，升级人工</button>
                </div>
            </div>
        `
        : `
            <div class="action-card muted-card">
                <h3>用户确认</h3>
                <p>当前状态不是 WAITING_USER_CONFIRM，无需执行用户确认。</p>
            </div>
        `;

    actionsPanelEl.innerHTML = approvalHtml + confirmHtml;
}

async function createTicket(payload, label = "工单") {
    try {
        const created = await api("/api/tickets", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(createResultEl, `${label}已创建：${created.ticketId}，初始返回状态 ${created.status}`);
        createForm.reset();
        createForm.elements.creatorId.value = "U1001";
        createForm.elements.creatorRole.value = "EMPLOYEE";
        createForm.elements.priority.value = "MEDIUM";
        await loadTicketWorkspace(created.ticketId);
    } catch (error) {
        setMessage(createResultEl, error.message, true);
    }
}

createForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(createForm);
    const payload = Object.fromEntries(formData.entries());
    await createTicket(payload);
});

document.querySelectorAll("[data-demo]").forEach((button) => {
    button.addEventListener("click", async () => {
        const demo = demoCases[button.dataset.demo];
        await createTicket({
            ...demo,
            creatorId: "U1001",
            creatorRole: "EMPLOYEE",
            priority: "MEDIUM"
        }, `Demo ${button.textContent} `);
    });
});

messageForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!selectedTicketId) {
        return;
    }
    const content = messageForm.elements.content.value.trim();
    if (!content) {
        setMessage(messageResultEl, "请输入补充信息。", true);
        return;
    }
    try {
        await api(`/api/tickets/${selectedTicketId}/messages`, {
            method: "POST",
            body: JSON.stringify({ content })
        });
        messageForm.elements.content.value = "";
        setMessage(messageResultEl, "补充信息已提交，Agent 正在重新理解并推进处理。");
        await loadTicketWorkspace(selectedTicketId, true);
    } catch (error) {
        setMessage(messageResultEl, error.message, true);
    }
});

actionsPanelEl.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
        return;
    }
    const action = target.dataset.action;
    if (!action || !selectedTicketId) {
        return;
    }
    try {
        if (action === "approve" || action === "reject") {
            const approvalId = target.dataset.approvalId;
            const approverId = document.getElementById("approver-id")?.value?.trim() || "AP2001";
            const comment = document.getElementById("approver-comment")?.value?.trim() || "";
            await api(`/api/approvals/${approvalId}/${action}`, {
                method: "POST",
                body: JSON.stringify({ approverId, comment })
            });
            setMessage(actionResultEl, action === "approve" ? "审批已通过，执行链路正在恢复。" : "审批已拒绝，工单已升级人工。");
        }
        if (action === "confirm-resolved" || action === "confirm-unresolved") {
            const comment = document.getElementById("confirm-comment")?.value?.trim() || "";
            await api(`/api/tickets/${selectedTicketId}/confirm`, {
                method: "POST",
                body: JSON.stringify({
                    resolved: action === "confirm-resolved",
                    comment
                })
            });
            setMessage(actionResultEl, action === "confirm-resolved" ? "用户确认已解决，工单已关闭。" : "用户反馈未解决，已升级人工接管。");
        }
        await loadTicketWorkspace(selectedTicketId, true);
    } catch (error) {
        setMessage(actionResultEl, error.message, true);
    }
});

refreshButton.addEventListener("click", async () => {
    try {
        await loadTickets();
        if (selectedTicketId) {
            await loadTicketWorkspace(selectedTicketId, true);
        }
    } catch (error) {
        setMessage(createResultEl, error.message, true);
    }
});

window.addEventListener("beforeunload", stopPolling);

loadTickets().catch((error) => setMessage(createResultEl, error.message, true));
