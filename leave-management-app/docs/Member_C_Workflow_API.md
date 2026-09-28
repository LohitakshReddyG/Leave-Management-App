# MEMBER C — Workflow Engine & REST API
## Leave Management App | Java + Spring Boot | 4-person parallel build via Antigravity + GitHub

You own the two scored cores of the problem statement: the **explicit approval state machine** (Manager → HR) and the **automatic escalation on timeout** — plus every REST endpoint. Get the state machine airtight; everything else is plumbing.

---

## YOUR OWNERSHIP (only you edit these)
```
src/main/java/com/hackathon/leave/service/ApprovalService.java
src/main/java/com/hackathon/leave/service/EscalationService.java
src/main/java/com/hackathon/leave/web/LeaveController.java
src/main/java/com/hackathon/leave/web/ApprovalController.java
src/main/java/com/hackathon/leave/web/GlobalExceptionHandler.java
src/main/java/com/hackathon/leave/dto/  (all DTO/view classes + mappers)
```
Everything else belongs to A, B, or D. Need a change elsewhere? Message the owner.

## SHARED CONTRACT (fixed agreement — build against these exactly)
- Base package `com.hackathon.leave`. Java 17, Maven, no Lombok.
- **Enums:** `Role {EMPLOYEE, MANAGER, HR}`; `RequestStatus {PENDING_MANAGER, PENDING_HR, APPROVED, REJECTED_BY_MANAGER, REJECTED_BY_HR, CANCELLED, ESCALATED}` — A's `RequestStatus` already contains the transition map and `canTransitionTo()`.
- **Entities (A owns):** `Employee, LeaveType, LeaveRequest, LeaveTransition, Notification` — field names per A's file. `LeaveRequest` has `@Version Long version` (optimistic locking) and `Instant pendingSince`.
- **Services you call (B owns — exact signatures):**
  - `LeaveService.apply(Long employeeId, String leaveTypeCode, LocalDate start, LocalDate end, String reason) → LeaveRequest`
  - `LeaveService.getBalance(Long employeeId, int year) → BalanceSummary` (`record BalanceSummary(int year, BigDecimal entitledDays, int usedDays, BigDecimal remainingDays)`)
  - `ConflictService.flagIfConflicted(LeaveRequest request)` — B calls it inside apply; you never call it.
- **You expose (B calls this):** `ApprovalService.recordTransition(Long requestId, Long actorId, RequestStatus from, RequestStatus to, String comment)` — make it public.
- **Repos available:** `EmployeeRepository`, `LeaveRequestRepository (findByEmployeeId, findByStatusAndPendingSinceBefore)`, `LeaveTransitionRepository (findByRequestIdOrderByAtAsc)`, `NotificationRepository`, `LeaveTypeRepository`.
- Config: `app.escalation.timeout-seconds=60` via `AppProperties.escalation.timeoutSeconds`.

## API CONTRACT (you implement; D's frontend and the curl smoke-tests depend on these EXACT paths and JSON)
JSON is camelCase; leave dates `yyyy-MM-dd`; timestamps ISO-8601.

| Method & path | Request body | Response |
|---|---|---|
| `POST /api/requests` | `{employeeId, leaveTypeCode, start, end, reason}` | 201 `LeaveRequestView` |
| `GET /api/requests?employeeId=` | — | `[LeaveRequestView]` |
| `GET /api/requests/pending/manager?managerId=` | — | `[LeaveRequestView]` (manager's team, status PENDING_MANAGER) |
| `GET /api/requests/pending/hr` | — | `[LeaveRequestView]` (status PENDING_HR) |
| `POST /api/requests/{id}/approve` | `{actorId, comment}` | 200 `LeaveRequestView` |
| `POST /api/requests/{id}/reject` | `{actorId, comment}` | 200 `LeaveRequestView` |
| `POST /api/requests/{id}/cancel` | `{actorId}` | 200 `LeaveRequestView` |
| `GET /api/requests/{id}/history` | — | `[{fromStatus, toStatus, actorName, comment, at}]` |
| `GET /api/employees?teamId=` | — | `[{id, name, email, role, teamId, joiningDate}]` |
| `GET /api/employees/{id}/balance?year=2026` | — | `{year, entitledDays, usedDays, remainingDays}` (map B's BalanceSummary) |
| `GET /api/notifications?managerId=` (or `?hr=true`) | — | `[{id, requestId, type, message, createdAt}]` |

`LeaveRequestView`: `{id, employeeName, leaveTypeCode, start, end, days, reason, status, conflictFlag, conflictDetail, createdAt}` — you build it by joining `LeaveRequest` with `Employee` and `LeaveType` (employeeName and leaveTypeCode are lookups, not entity fields).

**Error JSON (GlobalExceptionHandler):** `{"status": <int>, "message": "<text>"}` — 400 invalid input, 403 wrong actor, 404 not found, 409 illegal transition or version conflict. `OptimisticLockException` → 409 "request already decided".

## WHAT TO BUILD

### 1. `ApprovalService` — the state machine (0:15–0:50)
```java
private LeaveRequest transition(LeaveRequest r, RequestStatus target, Long actorId, String comment) {
    if (!r.getStatus().canTransitionTo(target))
        throw new ApiException(409, "Illegal transition " + r.getStatus() + " → " + target);
    RequestStatus from = r.getStatus();
    r.setStatus(target);
    if (target == PENDING_HR) r.setPendingSince(Instant.now());  // restart clock for HR stage
    transitionRepo.save(new LeaveTransition(r.getId(), actorId, from, target, comment, Instant.now()));
    notificationRepo.save(...);   // APPROVED / REJECTED_* to the employee
    return r;
}

public LeaveRequest decide(Long requestId, Long actorId, boolean approve, String comment) {
    LeaveRequest r = repo.findById(requestId).orElseThrow(() -> new ApiException(404, ...));
    Employee actor = employeeRepo.findById(actorId).orElseThrow(...);
    boolean mgrStage = r.getStatus() == PENDING_MANAGER, hrStage = r.getStatus() == PENDING_HR;

    if (mgrStage && !actor.getId().equals(findManagerOf(r).getId()))
        throw new ApiException(403, "Not the requesting employee's manager");
    if (hrStage && actor.getRole() != Role.HR)
        throw new ApiException(403, "Only HR decides at this stage");
    if (!mgrStage && !hrStage)
        throw new ApiException(409, "Request not pending a decision: " + r.getStatus());

    RequestStatus target = mgrStage
        ? (approve ? RequestStatus.PENDING_HR : RequestStatus.REJECTED_BY_MANAGER)
        : (approve ? RequestStatus.APPROVED     : RequestStatus.REJECTED_BY_HR);
    return transition(r, target, actorId, comment);
}

public LeaveRequest cancel(Long requestId, Long actorId) { /* actor==owner + PENDING_* → CANCELLED */ }
```
`@Transactional` on all three. The `@Version` field + these checks make it double-click-safe and two-approver-safe.

### 2. `EscalationService` — auto-escalation after timeout (0:50–1:20)
```java
@Scheduled(fixedDelay = 10_000)
@Transactional
public void escalateStaleRequests() {
    Instant cutoff = Instant.now().minusSeconds(appProperties.getEscalation().getTimeoutSeconds());
    for (LeaveRequest r : requestRepo.findByStatusAndPendingSinceBefore(PENDING_MANAGER, cutoff)) {
        transition(r, RequestStatus.ESCALATED, null, "auto: manager timeout");
        transition(r, RequestStatus.PENDING_HR, null, "auto: routed to HR after escalation");
        notificationRepo.save(new Notification(r.getId(), r.getEmployeeId(), "ESCALATION",
            "Request " + r.getId() + " escalated — manager did not act within timeout", Instant.now()));
    }
}
```
(`ESCALATED` is a pass-through state recorded for the audit trail, then routed to HR — both transitions visible in history. `@EnableScheduling` is already on A's main class.)

### 3. DTOs + mappers (1:20–1:40)
`LeaveRequestView`, `HistoryView`, `NotificationView`, mapper static methods (entity + employee + type → view). History endpoint joins transitions with employee names for `actorName`.

### 4. Controllers + `GlobalExceptionHandler` (1:40–2:20)
All 11 endpoints from the table. Apply endpoint: unwrap the DTO and call B's `LeaveService.apply(...)`. Manager queue: employees where `managerId = managerId` and status `PENDING_MANAGER`. Wrap `ApiException`, `OptimisticLockException`, `MethodArgumentNotValidException` into the standard error JSON.

### 5. Hardening (2:20–3:00)
Wrong actor → 403; double-approve → 409; malformed dates → 400; not-found → 404. Curl every path in the API table and record results.

## ANTIGRAVITY KICKOFF PROMPT (paste into your agent)
```
You are building the workflow + REST layer of a Spring Boot 3.3 (Java 17, no Lombok)
leave-management app. Build ONLY: ApprovalService, EscalationService, all files under
web/ and dto/ per this ownership list: [paste YOUR OWNERSHIP].
STUB RULE — dependencies not in my ownership (entities, repos, LeaveService,
ConflictService, AppProperties, ApiException) must be created as minimal STUBS in
package com.hackathon.leave.stubs, each marked // DELETE-BEFORE-INTEGRATION, with
exactly these shapes: [paste SHARED CONTRACT].
Implement the state machine + escalation exactly: [paste WHAT TO BUILD sections 1-2].
Implement every endpoint exactly: [paste API CONTRACT table].
Rules: every status change goes through transition() which validates
canTransitionTo() and writes a LeaveTransition audit row. Illegal transitions throw
ApiException(409). Approve/reject check actor permission (manager of the employee at
PENDING_MANAGER; role HR at PENDING_HR) → 403 otherwise. Escalation is @Scheduled
every 10s using appProperties timeout. Verify with `mvn spring-boot:run` and curl:
approve flow, reject flow, wrong actor 403, illegal transition 409.
```

## THE STUB STRATEGY (how you work before A/B push)
You need stubs for A's entities/repos and B's services (the agent creates them per the contract, marked `// DELETE-BEFORE-INTEGRATION`). At the 0:45 checkpoint (A's merge): pull, delete entity/repo stubs, swap imports. At the ~2:00 checkpoint (B's merge): pull, delete service stubs, wire real calls, re-run your curl suite. Budget 20 minutes total for both swaps.

## VERIFY BEFORE PUSHING (acceptance checklist)
- [ ] Manager-approve → PENDING_HR; HR-approve → APPROVED; reject at either stage works
- [ ] Wait 60s with a PENDING_MANAGER request → ESCALATED → PENDING_HR appears automatically + notification
- [ ] All 11 endpoints return the exact JSON shapes above (curl-checked)
- [ ] 403 / 409 / 404 / 400 all demonstrably return the error JSON
- [ ] History endpoint shows from → to, actor, comment, timestamp

## GIT DISCIPLINE
Branch: `feature/workflow-api` off `main`. Commit after every green curl run. Merge to `main` at the ~2:00–2:15 checkpoint (after B). Never edit others' files.
