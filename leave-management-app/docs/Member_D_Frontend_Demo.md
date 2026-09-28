# MEMBER D — Frontend, README & Demo Lead
## Leave Management App | Java + Spring Boot | 4-person parallel build via Antigravity + GitHub

You own everything the judges actually see: the three role-based interfaces, the history timeline (the state-machine proof), the README, and the live demo. You build fully independently all morning against **mock data**, then flip one flag at integration.

---

## YOUR OWNERSHIP (only you edit these)
```
src/main/resources/static/index.html
src/main/resources/static/app.js
src/main/resources/static/style.css
README.md
```
No framework, no build step — plain HTML + CSS + vanilla JS (fetch). Bootstrap 5 from CDN is allowed for speed. Everything must work by simply running the Spring Boot app and opening `http://localhost:8080/`.

## HOW YOU BUILD INDEPENDENTLY (the mock trick)
In `app.js`, line 1:
```js
const USE_MOCKS = true;   // flip to false at integration when the real API is merged
```
Every data call goes through one helper:
```js
async function api(method, path, body) {
    if (USE_MOCKS) return mock(method, path, body);
    const res = await fetch(path, {
        method, headers: {'Content-Type': 'application/json'},
        body: body ? JSON.stringify(body) : undefined
    });
    if (!res.ok) { const e = await res.json(); throw new Error(e.message); }
    return res.json();
}
```
`mock()` is a hand-written function returning the JSON below from hardcoded data (plus small state so the demo works standalone). This means you never wait for the backend — at integration you set `USE_MOCKS = false` and you're live.

## API CONTRACT (your fetches must use these EXACT paths and shapes)
JSON camelCase; dates `yyyy-MM-dd`; timestamps ISO-8601.

| Method & path | Request body | Response |
|---|---|---|
| `POST /api/requests` | `{employeeId, leaveTypeCode, start, end, reason}` | `LeaveRequestView` (201) |
| `GET /api/requests?employeeId=` | — | `[LeaveRequestView]` |
| `GET /api/requests/pending/manager?managerId=` | — | `[LeaveRequestView]` |
| `GET /api/requests/pending/hr` | — | `[LeaveRequestView]` |
| `POST /api/requests/{id}/approve` | `{actorId, comment}` | `LeaveRequestView` |
| `POST /api/requests/{id}/reject` | `{actorId, comment}` | `LeaveRequestView` |
| `POST /api/requests/{id}/cancel` | `{actorId}` | `LeaveRequestView` |
| `GET /api/requests/{id}/history` | — | `[{fromStatus, toStatus, actorName, comment, at}]` |
| `GET /api/employees?teamId=` | — | `[{id, name, email, role, teamId, joiningDate}]` |
| `GET /api/employees/{id}/balance?year=2026` | — | `{year, entitledDays, usedDays, remainingDays}` |
| `GET /api/notifications?managerId=` or `?hr=true` | — | `[{id, requestId, type, message, createdAt}]` |

`LeaveRequestView`: `{id, employeeName, leaveTypeCode, start, end, days, reason, status, conflictFlag, conflictDetail, createdAt}`

**Mock dataset (hardcode exactly this — it matches A's seed):**
```
1 Priya  MANAGER  team 1          2 Arjun  EMPLOYEE  joined 2020-01-01
3 Meera  EMPLOYEE  joined 2021    4 Kiran  EMPLOYEE  joined 2026-09-15 (balance: 6.7 entitled)
5 Ravi   EMPLOYEE  joined 2022    6 Divya  HR
Pre-approved leaves: Arjun 2026-10-05→10-09, Meera 2026-10-06→10-08
```

## WHAT TO BUILD

### 1. Shell (0:15–0:45)
Header: three tabs **Employee | Manager | HR**; login dropdown fed by `/api/employees` (mock list); a footer showing "logged in as …". Single `index.html` + one `app.js`. Tab switching just hides/shows sections.

### 2. Employee tab (0:45–1:30)
- **Balance card**: entitled / used / remaining (from balance endpoint). Make Kiran's 6.7 visually obvious — this is the pro-rating demo.
- **Apply form**: leave type select (ANNUAL/SICK/CASUAL), start + end date pickers, reason textarea. Client-side validation (end ≥ start), then POST; on error show the backend message ("insufficient balance" must render clearly — it's a demo beat).
- **My requests table**: dates, days, status pill, conflict badge, History button.

### 3. Manager tab (1:30–2:10)
Pending queue: employee name, dates, days, **CONFLICT badge (red pill)** — clicking it shows `conflictDetail` (who is off, which days). Approve (with comment prompt) and Reject (with comment) buttons per row.

### 4. HR tab (2:10–2:30)
Second-stage queue (same table layout) + an **Escalation banner**: polls notifications every 5s, shows ESCALATION notifications in red. Approve/Reject buttons.

### 5. History modal (2:30–3:00) — DO NOT CUT THIS
Vertical timeline rendering `/history`: `fromStatus → toStatus`, actor name, comment, timestamp, newest at bottom. This modal is the visual proof of the state machine — it's what judges remember.

### 6. Status pills + auto-refresh (3:00–3:15)
Colors: PENDING_* amber, APPROVED green, REJECTED_*/CANCELLED grey, ESCALATED red outline. `setInterval` re-fetches the visible tab's data every 5s.

### 7. README (3:15–3:30)
Sections: one-command run (`mvn spring-boot:run` → `http://localhost:8080`), 5-line architecture summary (state machine, escalation, conflict flagging, pro-rated balance), demo login IDs (Priya=1 manager, Ravi=5 employee, Divya=6 HR), what's configurable, known limitations. Judges read this first.

## ANTIGRAVITY KICKOFF PROMPT (paste into your agent)
```
Build the frontend of a leave-management app as plain HTML/CSS/vanilla JS files:
index.html, app.js, style.css in Spring Boot's static resources folder. No frameworks,
no build step, Bootstrap 5 via CDN is fine. Three role tabs (Employee / Manager / HR)
with a login dropdown. All data goes through an api(method, path, body) helper with a
USE_MOCKS flag at the top; when true, a mock() function serves hardcoded JSON for every
endpoint in this API contract: [paste API CONTRACT + mock dataset].
Build: Employee tab (balance card, apply form with validation and error display,
my-requests table), Manager tab (pending queue with red CONFLICT badge showing
conflictDetail on click, approve/reject with comment prompts), HR tab (queue +
escalation notifications banner), and a History modal (vertical timeline from the
history endpoint). Status pills colored: pending amber, approved green, rejected
grey, escalated red-outline. 5-second auto-refresh of the visible tab.
Must work when opened as a file with USE_MOCKS=true (no backend).
```

## VERIFY BEFORE PUSHING (acceptance checklist)
- [ ] Full demo walkthrough works with `USE_MOCKS = true` and zero backend
- [ ] All three tabs function; conflict badge, history modal, escalation banner all render
- [ ] After integration flip (`USE_MOCKS = false`): apply → approve → HR approve → history all live
- [ ] README complete with run command + demo logins
- [ ] Works in a normal browser window (test in the exact browser you'll present in)

## YOUR FINAL-HOUR ROLE — demo owner (3:15–4:00)
Rehearse the 8-step demo script (Integration Guide §4) as a narrative with the team. Suggested presenter split: you narrate, one member drives the browser, one stands by to answer the state-machine/escalation questions, one covers pro-rating math. Prepare the 60-second pitch:
"Requests move through an explicit, validated state machine — every transition audited. Managers who don't act within a configurable timeout get auto-escalated and the request routes to HR. Team conflicts are detected day-by-day and flagged for the approver — flagged, never auto-rejected. Balances are pro-rated from joining date — a September joiner sees 6.7 of 20 days, and pending requests count as in-flight. Runs with one command on H2."

## GIT DISCIPLINE
Branch: `feature/frontend` off `main`. Commit after each tab works. You can merge early and often — your files touch nothing else. The final integration commit is the single `USE_MOCKS = false` flip after C's API is merged. Never edit others' files.
