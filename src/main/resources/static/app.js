const listEl = document.getElementById("ticket-list");
const detailEl = document.getElementById("ticket-detail");
const createForm = document.getElementById("create-form");
const messageForm = document.getElementById("message-form");
const transitionForm = document.getElementById("transition-form");
const createResultEl = document.getElementById("create-result");
const messageResultEl = document.getElementById("message-result");
const transitionResultEl = document.getElementById("transition-result");
const refreshButton = document.getElementById("refresh-list");

let selectedTicketId = null;

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
    element.className = isError ? "muted error" : "muted ok";
}

async function loadTickets() {
    const tickets = await api("/api/tickets");
    listEl.innerHTML = "";
    if (tickets.length === 0) {
        listEl.innerHTML = '<p class="muted">还没有工单，先创建一条试试。</p>';
        return;
    }
    tickets.forEach((ticket) => {
        const card = document.createElement("button");
        card.type = "button";
        card.className = `ticket-card${ticket.ticketId === selectedTicketId ? " active" : ""}`;
        card.innerHTML = `
            <h3>${ticket.title}</h3>
            <div class="meta">${ticket.ticketId}</div>
            <div class="meta">状态：${ticket.status} | 优先级：${ticket.priority}</div>
        `;
        card.addEventListener("click", () => loadTicketDetail(ticket.ticketId));
        listEl.appendChild(card);
    });
}

async function loadTicketDetail(ticketId) {
    const ticket = await api(`/api/tickets/${ticketId}`);
    selectedTicketId = ticket.ticketId;
    messageForm.classList.remove("hidden");
    messageForm.elements.ticketId.value = ticket.ticketId;
    transitionForm.classList.remove("hidden");
    transitionForm.elements.ticketId.value = ticket.ticketId;
    transitionForm.elements.expectedVersion.value = ticket.version;
    detailEl.className = "detail";
    const slotsHtml = Object.entries(ticket.ticketContext.slots)
        .map(([key, value]) => `<div class="chip"><span>${key}</span><strong>${String(value)}</strong></div>`)
        .join("");
    const missingHtml = ticket.ticketContext.missingSlots.length === 0
        ? '<span class="badge ok-badge">已满足当前阶段要求</span>'
        : ticket.ticketContext.missingSlots.map((slot) => `<span class="badge warn-badge">${slot}</span>`).join("");
    detailEl.innerHTML = `
        <div class="detail-grid">
            <div><strong>工单号</strong><br>${ticket.ticketId}</div>
            <div><strong>状态</strong><br>${ticket.status}</div>
            <div><strong>创建人</strong><br>${ticket.creatorId} (${ticket.creatorRole})</div>
            <div><strong>版本</strong><br>${ticket.version}</div>
            <div><strong>优先级</strong><br>${ticket.priority}</div>
            <div><strong>风险级别</strong><br>${ticket.riskLevel}</div>
        </div>
        <div class="context-panel">
            <div class="section-head">
                <strong>Agent 结构化上下文</strong>
                <span class="meta">${ticket.ticketContext.lastAgentStep}</span>
            </div>
            <div class="detail-grid">
                <div><strong>识别意图</strong><br>${ticket.ticketContext.intent}</div>
                <div><strong>最近更新时间</strong><br>${ticket.ticketContext.updatedAt ?? "-"}</div>
            </div>
            <div>
                <strong>已知槽位</strong>
                <div class="chip-list">${slotsHtml || '<span class="muted">暂无已确认槽位</span>'}</div>
            </div>
            <div>
                <strong>缺失槽位</strong>
                <div class="badge-list">${missingHtml}</div>
            </div>
        </div>
        <div>
            <strong>描述</strong>
            <p>${ticket.description}</p>
        </div>
        <div class="detail-stack">
            <div>
                <strong>对话记录</strong>
                <div class="message-list">
                    ${ticket.conversationMessages.map((message) => `
                        <div class="message ${message.role === "AGENT" ? "agent" : "user"}">
                            <div class="message-head">
                                <span>${message.role}</span>
                                <span class="meta">${message.messageType}</span>
                            </div>
                            <div>${message.content}</div>
                        </div>
                    `).join("")}
                </div>
            </div>
            <div>
                <strong>Agent 节点日志</strong>
                <div class="history">
                    ${ticket.agentStepLogs.map((item) => `
                        <div class="history-item">
                            <div>${item.nodeName} <span class="meta">${item.status}</span></div>
                            <div class="meta">${JSON.stringify(item.output)}</div>
                        </div>
                    `).join("")}
                </div>
            </div>
        </div>
        <div>
            <strong>状态历史</strong>
            <div class="history">
                ${ticket.statusHistory.map((item) => `
                    <div class="history-item">
                        <div>${item.fromStatus ?? "NONE"} -> ${item.toStatus}</div>
                        <div class="meta">${item.actorId} (${item.actorRole}) ${item.comment ?? ""}</div>
                    </div>
                `).join("")}
            </div>
        </div>
    `;
    await loadTickets();
}

createForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(createForm);
    const payload = Object.fromEntries(formData.entries());
    try {
        const created = await api("/api/tickets", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(createResultEl, `已创建 ${created.ticketId}，初始状态 ${created.status}`);
        createForm.reset();
        await loadTickets();
        await loadTicketDetail(created.ticketId);
    } catch (error) {
        setMessage(createResultEl, error.message, true);
    }
});

messageForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(messageForm);
    const payload = Object.fromEntries(formData.entries());
    try {
        const updated = await api(`/api/tickets/${payload.ticketId}/messages`, {
            method: "POST",
            body: JSON.stringify({ content: payload.content })
        });
        setMessage(messageResultEl, "补充信息已写入，Agent 已重新理解当前工单。");
        messageForm.elements.content.value = "";
        await loadTicketDetail(updated.ticketId);
    } catch (error) {
        setMessage(messageResultEl, error.message, true);
    }
});

transitionForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(transitionForm);
    const payload = Object.fromEntries(formData.entries());
    payload.expectedVersion = Number(payload.expectedVersion);
    try {
        const updated = await api(`/api/tickets/${payload.ticketId}/status`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(transitionResultEl, `状态已更新为 ${updated.status}`);
        await loadTicketDetail(updated.ticketId);
    } catch (error) {
        setMessage(transitionResultEl, error.message, true);
    }
});

refreshButton.addEventListener("click", async () => {
    await loadTickets();
    if (selectedTicketId) {
        await loadTicketDetail(selectedTicketId);
    }
});

loadTickets().catch((error) => setMessage(createResultEl, error.message, true));
