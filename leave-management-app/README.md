# ClockIt — Leave Management App with Approval Chains

Team ClockIt · Java problem statement 2 · Spring Boot 3 / Java 17 / H2

React frontend, Spring Boot backend. A leave workflow where every request
walks an explicit, audited state machine:
manager approves first, HR confirms second, idle requests escalate
automatically, team conflicts are flagged for the approver (never
auto-rejected), and balances are pro-rated from the joining date.

## Run

Requires Java 17+ and Maven 3.8+. The repo ships with the React build already
compiled into the jar, so the one-command demo needs no Node:

```
mvn spring-boot:run
```

Open **http://localhost:8080** — the React UI, the API and the H2 console all
come from the same process.

### Frontend (React + Vite)

The frontend is a React 18 + Vite app in `frontend/`.

- **Rebuild the UI** (after editing any component): `cd frontend && npm install && npm run build`
  — Vite outputs straight into `src/main/resources/static/`, then `mvn spring-boot:run` serves it.
- **Dev mode with hot reload**: start the backend (`mvn spring-boot:run`), then
  `cd frontend && npm install && npm run dev` and open **http://localhost:5173** —
  `/api` is proxied to port 8080. Data lives in `./leavedb.*` files (delete them to
re-seed from scratch). H2 console: http://localhost:8080/h2-console (JDBC URL
`jdbc:h2:file:./leavedb`, user `sa`, empty password).

### Demo logins (pick from the dropdown)

| Person | Role | Notes |
|--------|------|-------|
| Priya  | Manager | team 1's manager — sees the approval queue with conflict flags |
| Arjun, Meera | Employee | hold pre-approved overlapping leaves 5–9 Oct 2026 |
| Kiran  | Employee | **joined 15 Sep 2026 → 6.7 of 20 annual days (pro-rating demo)** |
| Ravi   | Employee | use this one to trigger the conflict demo |
| Divya  | HR     | second-stage approvals + escalation inbox |

## The 8-step demo script

1. **Conflict flag**: as Ravi, apply 2026-10-05 → 2026-10-09. Accepted, but
   flagged CONFLICT (Arjun and Meera are already off) — flag, not reject.
2. **Manager approval**: as Priya, approve Ravi's request (with a comment) →
   status PENDING_HR.
3. **HR approval**: as Divya, approve → APPROVED. Open History — the full
   timeline null → PENDING_MANAGER → PENDING_HR → APPROVED.
4. **Rejection**: apply again as Ravi; as Priya, reject → REJECTED_BY_MANAGER.
5. **Pro-rating**: as Kiran, the balance card shows 6.7 entitled days; apply
   for 8 days → clean 400 "insufficient balance".
6. **Escalation**: as Arjun, apply 2026-12-01 → 2026-12-04 and leave it idle.
   Within ~70 s the HR tab shows the escalation banner and the request sits in
   the HR queue, history shows PENDING_MANAGER → ESCALATED → PENDING_HR.
7. **Guards**: approve someone else's request as the wrong actor → 403;
   double-approve fast → 409; weekend-only range → 400.
8. **Durability**: Ctrl-C the app, `mvn spring-boot:run` again — everything is
   still there (H2 file store).

## Architecture

```
React + Vite app in frontend/ (role tabs: employee · manager · HR)  ← Member D
  │ fetch JSON, served from static/ after `npm run build`
REST controllers: 12 endpoints, one error shape        ← Member C
  │
services:
  ApprovalService   explicit state machine + audit rows (Member C)
  EscalationService  @Scheduled scan → ESCALATED → PENDING_HR (Member C)
  LeaveService       working days, pro-rated balances, apply (Member B)
  ConflictService   per-day team overlap scan, flag only (Member B)
  SeedDataService    demo team, idempotent              (Member A)
  │
JPA entities + H2 file DB, @Version optimistic locking  (Member A)
```

State machine (`RequestStatus.ALLOWED`):

```
PENDING_MANAGER → PENDING_HR | REJECTED_BY_MANAGER | CANCELLED | ESCALATED
PENDING_HR      → APPROVED    | REJECTED_BY_HR      | CANCELLED
ESCALATED       → PENDING_HR        (timeout routes the request to HR)
APPROVED / REJECTED_* / CANCELLED    (terminal)
```

## API

| Method | Path | Body / params | Returns |
|---|---|---|---|
| POST | `/api/requests` | `{employeeId, leaveTypeCode, start, end, reason}` | 201 `LeaveRequestView` |
| GET | `/api/requests?employeeId=` | | `[LeaveRequestView]` |
| GET | `/api/requests/pending/manager?managerId=` | | `[LeaveRequestView]` |
| GET | `/api/requests/pending/hr` | | `[LeaveRequestView]` |
| POST | `/api/requests/{id}/approve` | `{actorId, comment}` | `LeaveRequestView` |
| POST | `/api/requests/{id}/reject` | `{actorId, comment}` | `LeaveRequestView` |
| POST | `/api/requests/{id}/cancel` | `{actorId}` | `LeaveRequestView` |
| GET | `/api/requests/{id}/history` | | `[{fromStatus,toStatus,actorName,comment,at}]` |
| GET | `/api/employees?teamId=` | | `[EmployeeView]` |
| GET | `/api/employees/{id}/balance?year&type` | | `{leaveTypeCode,year,entitledDays,usedDays,remainingDays}` |
| GET | `/api/leave-types` | | `[{code,annualQuotaDays}]` |
| GET | `/api/notifications?managerId=` or `?hr=true` | | `[NotificationView]` |

Errors are always `{"status": n, "message": "..."}` — 400 invalid input,
403 wrong actor, 404 not found, 409 illegal transition / version conflict.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `app.escalation.timeout-seconds` | 60 | PENDING_MANAGER older than this escalates to HR. Demo value; production would use days. |
| `app.conflict.max-simultaneous` | 2 | Teammates already off before a request is conflict-flagged. |

## Notes and known limitations

- Pending requests count against the balance (in-flight), so two concurrent
  applies cannot double-spend the same days.
- A leave spanning a year boundary counts its full day total against the
  starting year.
- Escalation `@Scheduled` scan runs every 10 s; ESCALATED is a recorded
  pass-through state, so the audit trail shows the route to HR.
- Authentication is demo-level (the logged-in employee id travels in the
  request); Spring Security would drop in cleanly in front of the same API.
- Tests: `mvn test` runs the working-day and pro-rating math suite.
