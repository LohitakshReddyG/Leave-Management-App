/* ClockIt — leave management frontend. Vanilla JS, no dependencies.
 * Role tabs gate what the logged-in user can do; the backend is the real
 * guard (403 / 409 on the wrong actor or an illegal transition).
 */
"use strict";

let me = null;
let activeTab = "employee";
const YEAR = new Date().getFullYear();

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

/* ---------------- API helper ---------------- */
async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let data = null;
  try { data = await res.json(); } catch (_) { /* no body */ }
  if (!res.ok) {
    const msg = (data && data.message) ? data.message : "HTTP " + res.status;
    throw new Error(msg);
  }
  return data;
}

/* ---------------- small helpers ---------------- */
function notice(text, kind) {
  const el = $("#notice");
  el.textContent = text;
  el.className = "notice " + (kind || "");
  el.hidden = !text;
  clearTimeout(notice._t);
  notice._t = setTimeout(() => { el.hidden = true; }, 6000);
}

function fmtDate(iso) {
  if (!iso) return "–";
  const [y, m, d] = iso.split("-");
  return `${d}/${m}/${y}`;
}

function fmtWhen(iso) {
  if (!iso) return "";
  const dt = new Date(iso);
  return dt.toLocaleString();
}

function statusPill(status) {
  const cls = status.startsWith("PENDING") ? "pending"
    : status === "APPROVED" ? "approved"
    : status === "ESCALATED" ? "escalated"
    : "rejected"; // rejected_* / cancelled
  return `<span class="pill ${cls}">${status.replace(/_/g, " ")}</span>`;
}

function conflictCell(v) {
  if (!v.conflictFlag) return `<span style="color:#9aa2b1">—</span>`;
  const detail = (v.conflictDetail || "").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  return `<span class="badge-conflict" title="${detail}">CONFLICT</span>`;
}

function datesCell(v) {
  return `${fmtDate(v.start)} → ${fmtDate(v.end)}`;
}

/* ---------------- login + tabs ---------------- */
async function loadEmployees() {
  const list = await api("GET", "/api/employees");
  const sel = $("#userSelect");
  sel.innerHTML = list.map(e =>
    `<option value="${e.id}">${e.name} (${e.role})</option>`).join("");
  const saved = localStorage.getItem("clockit-user");
  if (saved && list.some(e => String(e.id) === saved)) sel.value = saved;
  sel.onchange = () => { localStorage.setItem("clockit-user", sel.value); setMe(); };
  setMe();
}

function setMe() {
  const id = Number($("#userSelect").value);
  api("GET", "/api/employees").then(list => {
    me = list.find(e => e.id === id) || null;
    if (!me) return;
    $("#rolePill").hidden = false;
    $("#rolePill").textContent = me.role;
    // role-gated tabs
    $('[data-tab="manager"]').hidden = me.role !== "MANAGER";
    $('[data-tab="hr"]').hidden = me.role !== "HR";
    switchTab("employee");
    refreshAll();
  });
}

function switchTab(tab) {
  activeTab = tab;
  $$(".tab").forEach(b => b.classList.toggle("active", b.dataset.tab === tab));
  $$(".panel").forEach(p => p.classList.toggle("active", p.id === "tab-" + tab));
  refreshAll();
}

/* ---------------- employee tab ---------------- */
async function refreshEmployee() {
  if (!me) return;
  const year = YEAR;
  const bal = await api("GET", `/api/employees/${me.id}/balance?year=${year}&type=ANNUAL`);
  $("#balanceTitle").textContent = `Balance · ANNUAL ${year}`;
  $("#balEntitled").textContent = bal.entitledDays;
  $("#balUsed").textContent = bal.usedDays;
  $("#balRemaining").textContent = bal.remainingDays;
  $("#balHint").textContent = me.joiningDate && Number(me.joiningDate.slice(0, 4)) === year
    ? `Pro-rated from joining on ${fmtDate(me.joiningDate)} — quota × months remaining ÷ 12.`
    : `Full-year quota; pending requests count as used.`;

  const list = await api("GET", `/api/requests?employeeId=${me.id}`);
  const tbody = $("#myRequestsTable tbody");
  tbody.innerHTML = list.length ? list.map(v => `
    <tr>
      <td>${v.id}</td>
      <td>${v.leaveTypeCode}</td>
      <td>${datesCell(v)}</td>
      <td>${v.days}</td>
      <td>${statusPill(v.status)}</td>
      <td>${conflictCell(v)}</td>
      <td>
        <button class="btn small ghost" onclick="showHistory(${v.id})">History</button>
        ${v.status.startsWith("PENDING") ? `<button class="btn small reject" onclick="cancelReq(${v.id})">Cancel</button>` : ""}
      </td>
    </tr>`).join("")
    : `<tr><td colspan="7" class="empty">No requests yet — apply above.</td></tr>`;
}

async function loadLeaveTypes() {
  const types = await api("GET", "/api/leave-types");
  $("#leaveType").innerHTML = types.map(t =>
    `<option value="${t.code}">${t.code} (quota ${t.annualQuotaDays})</option>`).join("");
}

$("#applyForm")?.addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    const r = await api("POST", "/api/requests", {
      employeeId: me.id,
      leaveTypeCode: $("#leaveType").value,
      start: $("#startDate").value,
      end: $("#endDate").value,
      reason: $("#reason").value,
    });
    notice(`Request #${r.id} submitted — ${r.days} working day(s), now PENDING MANAGER.`
      + (r.conflictFlag ? " Team conflict flagged — the manager will see it." : ""), "ok");
    $("#applyForm").reset();
    refreshEmployee();
  } catch (err) {
    notice(err.message, "err");
  }
});

async function cancelReq(id) {
  try {
    await api("POST", `/api/requests/${id}/cancel`, { actorId: me.id });
    notice(`Request #${id} cancelled.`, "ok");
    refreshEmployee();
  } catch (err) { notice(err.message, "err"); }
}

/* ---------------- manager tab ---------------- */
async function refreshManager() {
  if (!me || me.role !== "MANAGER") return;
  const list = await api("GET", `/api/requests/pending/manager?managerId=${me.id}`);
  $("#mgrCount").textContent = list.length ? `(${list.length})` : "";
  $("#managerQueueTable tbody").innerHTML = list.length ? list.map(v => `
    <tr>
      <td>${v.id}</td>
      <td><b>${v.employeeName}</b></td>
      <td>${v.days}</td>
      <td>${datesCell(v)}</td>
      <td>${statusPill(v.status)}</td>
      <td>${conflictCell(v)}</td>
      <td>
        <button class="btn small approve" onclick="decide(${v.id}, 'approve')">Approve</button>
        <button class="btn small reject" onclick="decide(${v.id}, 'reject')">Reject</button>
      </td>
    </tr>`).join("")
    : `<tr><td colspan="7" class="empty">Nothing pending.</td></tr>`;

  const feed = await api("GET", `/api/notifications?managerId=${me.id}`);
  $("#managerFeed").innerHTML = feed.length ? feed.map(n => `
    <li class="${n.type}">
      <b>${n.type}</b> — request #${n.requestId}: ${n.message}
      <span class="meta">${fmtWhen(n.createdAt)}</span>
    </li>`).join("")
    : `<li class="empty">No notifications.</li>`;
}

/* ---------------- HR tab ---------------- */
async function refreshHR() {
  if (!me || me.role !== "HR") return;
  const list = await api("GET", "/api/requests/pending/hr");
  $("#hrCount").textContent = list.length ? `(${list.length})` : "";
  $("#hrQueueTable tbody").innerHTML = list.length ? list.map(v => `
    <tr>
      <td>${v.id}</td>
      <td><b>${v.employeeName}</b></td>
      <td>${v.days}</td>
      <td>${datesCell(v)}</td>
      <td>${statusPill(v.status)}</td>
      <td>${conflictCell(v)}</td>
      <td>
        <button class="btn small approve" onclick="decide(${v.id}, 'approve')">Approve</button>
        <button class="btn small reject" onclick="decide(${v.id}, 'reject')">Reject</button>
      </td>
    </tr>`).join("")
    : `<tr><td colspan="7" class="empty">Nothing pending.</td></tr>`;

  const feed = await api("GET", "/api/notifications?hr=true");
  const escalations = feed.filter(n => n.type === "ESCALATION");
  const banner = $("#escalationBanner");
  banner.hidden = escalations.length === 0;
  banner.textContent = escalations.length
    ? `⚑ ${escalations.length} escalation${escalations.length > 1 ? "s" : ""}: `
      + escalations.map(n => `request #${n.requestId} (${fmtWhen(n.createdAt)})`).join(" · ")
    : "";
  $("#hrFeed").innerHTML = feed.length ? feed.map(n => `
    <li class="${n.type}">
      <b>${n.type}</b> — request #${n.requestId}: ${n.message}
      <span class="meta">${fmtWhen(n.createdAt)}</span>
    </li>`).join("")
    : `<li class="empty">No notifications.</li>`;
}

/* ---------------- decisions ---------------- */
async function decide(id, action) {
  const comment = prompt(`Comment for this ${action} (optional):`) || "";
  try {
    const r = await api("POST", `/api/requests/${id}/${action}`, { actorId: me.id, comment });
    notice(`Request #${id} → ${r.status.replace(/_/g, " ")}.`, "ok");
    refreshAll();
  } catch (err) { notice(err.message, "err"); }
}

/* ---------------- history modal ---------------- */
async function showHistory(id) {
  const list = await api("GET", `/api/requests/${id}/history`);
  $("#modalReqId").textContent = `#${id}`;
  $("#timeline").innerHTML = list.map(h => `
    <li>
      <div class="move">${h.fromStatus ? h.fromStatus.replace(/_/g, " ") : "∅"}
        <span class="arr">→</span> ${h.toStatus.replace(/_/g, " ")}</div>
      <div class="sub">${h.comment || ""} · by ${h.actorName} · ${fmtWhen(h.at)}</div>
    </li>`).join("");
  $("#modalBackdrop").hidden = false;
}

$("#modalClose")?.addEventListener("click", () => { $("#modalBackdrop").hidden = true; });
$("#modalBackdrop")?.addEventListener("click", (e) => {
  if (e.target.id === "modalBackdrop") $("#modalBackdrop").hidden = true;
});

/* ---------------- refresh loop ---------------- */
function refreshAll() {
  refreshEmployee().catch(() => {});
  refreshManager().catch(() => {});
  refreshHR().catch(() => {});
}

$$(".tab").forEach(b => b.addEventListener("click", () => switchTab(b.dataset.tab)));

setInterval(() => {
  $("#refreshNote").textContent = "auto-refresh 5 s";
  refreshAll();
}, 5000);

/* ---------------- boot ---------------- */
loadLeaveTypes().catch(() => {});
loadEmployees().catch((e) => notice("Could not reach the API: " + e.message, "err"));
