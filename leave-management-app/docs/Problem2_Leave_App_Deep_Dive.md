# Problem 2 Deep Dive — Leave Management App with Approval Chains
## Java + Spring Boot, 4-hour build

Companion to the general build guide. This document is the implementation blueprint: everything here is meant to be typed out directly. Code is complete for the scored core (state machine, pro-rating, conflict detection, escalation); boilerplate is sketched.

---

## 1. Project setup (0:00–0:15)

Generate at `start.spring.io`: **Spring Boot 3.x, Java 17, Maven**, dependencies:
- Spring Web
- Spring Data JPA
- H2 Database
- Validation
- Lombok (optional — skip if it slows you down)

`application.properties`:
```properties
spring.datasource.url=jdbc:h2:file:./leavedb;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true
spring.jpa.open-in-view=false
# escalation: manager must act within this many seconds (demo value; "3 days" in prod)
app.escalation.timeout-seconds=60
# conflict threshold: max simultaneous leavers allowed per team before flagging
app.conflict.max-simultaneous=2
```

`app.conflict.*` / `app.escalation.*` are read via an `@ConfigurationProperties` class — this proves "configurable" to judges.

## 2. Package structure

```
com.hackathon.leave
├── LeaveApplication.java
├── config/AppProperties.java
├── model/  Employee, Role, LeaveType, LeaveRequest, RequestStatus, LeaveTransition, Notification
├── repo/   EmployeeRepository, LeaveRequestRepository, LeaveTransitionRepository,
│           NotificationRepository, LeaveTypeRepository
├── service/ LeaveService (apply/cancel/balance)
│            ApprovalService (state machine: approve/reject)
│            EscalationService (@Scheduled)
│            ConflictService (team conflict flagging)
│            SeedDataService (CommandLineRunner)
├── web/    LeaveController, ApprovalController, EmployeeController, GlobalExceptionHandler
└── dto/    ApplyRequestDto, LeaveRequestView, DecisionDto, BalanceView, HistoryView
```

## 3. Model layer (0:15–0:40)

### Enums
```java
public enum Role { EMPLOYEE, MANAGER, HR }

public enum RequestStatus {
    PENDING_MANAGER, PENDING_HR, APPROVED,
    REJECTED_BY_MANAGER, REJECTED_BY_HR, CANCELLED, ESCALATED
}
```

### State machine — the single source of truth
```java
public enum RequestStatus {
    PENDING_MANAGER, PENDING_HR, APPROVED,
    REJECTED_BY_MANAGER, REJECTED_BY_HR, CANCELLED, ESCALATED;

    public static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = Map.of(
        PENDING_MANAGER, Set.of(PENDING_HR, REJECTED_BY_MANAGER, CANCELLED, ESCALATED),
        PENDING_HR,      Set.of(APPROVED, REJECTED_BY_HR, CANCELLED),
        ESCALATED,       Set.of(PENDING_HR),
        APPROVED,        Set.of(),
        REJECTED_BY_MANAGER, Set.of(),
        REJECTED_BY_HR,  Set.of(),
        CANCELLED,       Set.of()
    );

    public boolean canTransitionTo(RequestStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
```

### Entities
```java
@Entity public class Employee {
    @Id @GeneratedValue Long id;
    String name, email;                  // email used as login key for demo
    @Enumerated(EnumType.STRING) Role role;
    Long managerId;                      // null for HR / top manager
    Long teamId;
    LocalDate joiningDate;              // drives pro-rating
}

@Entity public class LeaveType {
    @Id @GeneratedValue Long id;
    String code;                         // ANNUAL, SICK, CASUAL
    int annualQuotaDays;                 // e.g. ANNUAL=20
}

@Entity public class LeaveRequest {
    @Id @GeneratedValue Long id;
    Long employeeId;
    Long leaveTypeId;
    LocalDate startDate, endDate;
    int days;                            // working days, computed at apply
    String reason;
    @Enumerated(EnumType.STRING) RequestStatus status;
    boolean conflictFlag;
    String conflictDetail;               // JSON-ish text: who overlaps, when
    Instant createdAt;
    Instant pendingSince;                // reset on each stage change; drives escalation
    @Version Long version;              // optimistic locking: two approvers can't double-decide
}

@Entity public class LeaveTransition {
    @Id @GeneratedValue Long id;
    Long requestId, actorId;
    RequestStatus fromStatus, toStatus;
    String comment;
    Instant at;
}

@Entity public class Notification {
    @Id @GeneratedValue Long id;
    Long requestId;
    String type;                         // ESCALATION, APPROVED, REJECTED, CONFLICT, INFO
    String message;
    Instant createdAt;
}
```

## 4. Core services (0:40–2:00) — the scored part

### 4a. Working-day count + pro-rated entitlement (put in `LeaveService`)

```java
static int workingDays(LocalDate start, LocalDate end) {
    int d = 0;
    for (LocalDate cur = start; !cur.isAfter(end); cur = cur.plusDays(1))
        if (cur.getDayOfWeek() != DayOfWeek.SATURDAY
         && cur.getDayOfWeek() != DayOfWeek.SUNDAY) d++;
    return d;
}

static BigDecimal entitledDays(LocalDate joining, int quotaDays, int year) {
    if (joining.getYear() != year) return BigDecimal.valueOf(quotaDays);
    long monthsFromJoinInclusive = 12 - joining.getMonthValue() + 1;
    return BigDecimal.valueOf(quotaDays)
            .multiply(BigDecimal.valueOf(monthsFromJoinInclusive))
            .divide(BigDecimal.valueOf(12), 1, RoundingMode.HALF_UP);
}
// Example: joining 2026-09-15, ANNUAL quota 20 → 20 × 4/12 = 6.7 days
```

### 4b. Balance — computed, never stored (avoids drift)

```java
@Transactional(readOnly = true)
public BalanceView getBalance(Long employeeId, int year) {
    var emp = employeeRepo.findById(employeeId).orElseThrow();
    var lt = leaveTypeRepo.findByCode("ANNUAL");
    BigDecimal entitled = entitledDays(emp.getJoiningDate(), lt.getAnnualQuotaDays(), year);

    int usedDays = requestRepo.findByEmployeeIdAndStatusNot(employeeId, ...)
        // statuses: APPROVED, PENDING_MANAGER, PENDING_HR, ESCALATED
        .filter(r -> overlapsYear(r, year))
        .mapToInt(LeaveRequest::getDays)
        .sum();
    // Pending requests count as "in flight" so two concurrent applies can't double-spend
    return new BalanceView(entitled, usedDays, entitled.subtract(BigDecimal.valueOf(usedDays)));
}
```

### 4c. Apply flow (`LeaveService.apply`)

```java
@Transactional
public LeaveRequestView apply(ApplyRequestDto dto) {
    // 1. Validate: end >= start, future dates, type exists
    // 2. days = workingDays(start, end); reject if 0 (weekend-only)
    // 3. Balance check (4b): if days > remaining → 400 "insufficient balance"
    // 4. Create request: status = PENDING_MANAGER, createdAt = pendingSince = now
    // 5. conflictService.flagIfConflicted(request)   // see 4e — flag, never reject
    // 6. Write transition row: null → PENDING_MANAGER (actor = employee)
    // 7. If conflictFlag: write a CONFLICT notification for manager
}
```

### 4d. State machine enforcement (`ApprovalService`)

```java
@Transactional
public LeaveRequestView decide(Long requestId, Long actorId, boolean approve, String comment) {
    LeaveRequest r = requestRepo.findById(requestId).orElseThrow();
    var actor = employeeRepo.findById(actorId).orElseThrow();
    boolean atManagerStage = r.getStatus() == PENDING_MANAGER;
    boolean atHrStage = r.getStatus() == PENDING_HR;

    // who is allowed at this stage
    if (atManagerStage && !actor.getManagerId().equals(r.getEmployeeId()))
        throw new ApiException(403, "Not the requesting employee's manager");
    if (atHrStage && actor.getRole() != Role.HR)
        throw new ApiException(403, "Only HR decides at this stage");
    if (!atManagerStage && !atHrStage)
        throw new ApiException(409, "Request is not pending a decision: " + r.getStatus());

    RequestStatus target = atManagerStage
        ? (approve ? PENDING_HR : REJECTED_BY_MANAGER)
        : (approve ? APPROVED     : REJECTED_BY_HR);

    return transition(r, target, actorId, comment);
}

private LeaveRequestView transition(LeaveRequest r, RequestStatus target,
                                    Long actorId, String comment) {
    if (!r.getStatus().canTransitionTo(target))
        throw new ApiException(409, "Illegal transition "
            + r.getStatus() + " → " + target);
    var from = r.getStatus();
    r.setStatus(target);
    r.setPendingSince(target == PENDING_HR ? Instant.now() : r.getPendingSince());
    transitionRepo.save(new LeaveTransition(r.getId(), actorId, from, target, comment, Instant.now()));
    // notification rows: APPROVED / REJECTED_* to employee
    return toView(r);
}
```
`@Version` + this method = double-click-safe and race-safe (concurrent approve throws `OptimisticLockException` → 409).

### 4e. Conflict detection (`ConflictService`) — flag, don't reject

```java
@Transactional
public void flagIfConflicted(LeaveRequest r) {
    var me = employeeRepo.findById(r.getEmployeeId()).orElseThrow();
    var team = employeeRepo.findByTeamId(me.getTeamId());           // includes me
    int threshold = props.getConflict().getMaxSimultaneous();       // app config

    // any day of my range where >= threshold teammates are already off
    Map<LocalDate, List<String>> overlaps = new HashMap<>();
    for (LocalDate day : eachDay(r.getStartDate(), r.getEndDate())) {
        List<String> offSameDay = team.stream()
            .filter(m -> !m.getId().equals(r.getEmployeeId()))
            .filter(m -> requestRepo.existsActiveLeaveOn(m.getId(), day)) // JPQL: status IN (PENDING_MANAGER,PENDING_HR,ESCALATED,APPROVED) AND start<=day AND end>=day
            .map(Employee::getName).toList();
        if (offSameDay.size() >= threshold) overlaps.put(day, offSameDay);
    }
    if (!overlaps.isEmpty()) {
        r.setConflictFlag(true);
        r.setConflictDetail("Team conflict: " + overlaps.size() + " day(s) over limit. "
            + overlaps.entrySet().stream()
                .map(e -> e.getKey() + " → off: " + e.getValue())
                .collect(Collectors.joining("; ")));
    }
}
```
Run it **at apply time** and re-run at manager approval time (team state may have changed).

### 4f. Escalation (`EscalationService`) — automatic after timeout

```java
@Service
public class EscalationService {
    @Scheduled(fixedDelay = 10_000)   // scan every 10s; timeout from config
    @Transactional
    public void escalateStaleRequests() {
        Instant cutoff = Instant.now()
            .minusSeconds(props.getEscalation().getTimeoutSeconds());
        List<LeaveRequest> stale = requestRepo
            .findByStatusAndPendingSinceBefore(PENDING_MANAGER, cutoff);
        for (LeaveRequest r : stale) {
            // ESCALATED is a pass-through state recorded for audit, then routed to HR
            transition(r, ESCALATED, null, "auto: manager timeout");
            transition(r, PENDING_HR, null, "auto: routed to HR after escalation");
            notificationRepo.save(new Notification(r.getId(), "ESCALATION",
                "Request " + r.getId() + " escalated — manager did not act in "
                + props.getEscalation().getTimeoutSeconds() + "s", Instant.now()));
        }
    }
}
```
Enable scheduling on the main class: `@SpringBootApplication @EnableScheduling`.

## 5. REST API contract (2:00–2:20)

| Method | Path | Actor | Purpose |
|---|---|---|---|
| GET | `/api/employees?teamId=` | any | list (for demo logins) |
| POST | `/api/requests` `{employeeId, leaveTypeCode, start, end, reason}` | employee | apply |
| GET | `/api/requests?employeeId=` | employee | my requests |
| GET | `/api/requests/pending/manager?managerId=` | manager | manager queue (with conflict flags) |
| GET | `/api/requests/pending/hr` | HR | HR + escalation queue |
| POST | `/api/requests/{id}/approve` `{actorId, comment}` | manager/HR | advance state machine |
| POST | `/api/requests/{id}/reject` `{actorId, comment}` | manager/HR | reject at current stage |
| POST | `/api/requests/{id}/cancel` `{actorId}` | employee | cancel own pending |
| GET | `/api/requests/{id}/history` | any | transition audit timeline |
| GET | `/api/employees/{id}/balance?year=` | any | entitled / used / remaining |
| GET | `/api/notifications?recipient=managerId` | any | escalation + conflict inbox |
| GET | `/api/team/{teamId}/calendar?month=` | any | team leave calendar (nice-to-have) |

Passing `actorId` in the body/query keeps auth out of the way; note in the README that Spring Security would layer on cleanly. Keep one `GlobalExceptionHandler` mapping `ApiException →` JSON `{status, message}`.

## 6. Frontend (2:20–3:20) — one page, three role tabs

Single `static/index.html` + vanilla JS (fetch). Structure:

- **Header**: role tabs `Employee | Manager | HR` + a login dropdown (just picks an employee from `/api/employees` and stores their id in a JS var).
- **Employee tab**: balance card (entitled / used / remaining); apply form (type, start, end, reason); "My requests" table with status pill + a History link; my notifications.
- **Manager tab**: pending queue table — employee, dates, days, **conflict badge** (red pill `CONFLICT` with tooltip = `conflictDetail`), Approve / Reject buttons with comment prompt.
- **HR tab**: pending second-stage queue + escalation notifications banner; same approve/reject; a filter "show dead/terminal requests".

Status pills colored: pending=amber, approved=green, rejected/cancelled=grey, escalated=red-outline.
Refresh: a 5s `setInterval` re-fetch of the visible tab's table — feels live, costs 10 lines.
History: modal that renders `/api/requests/{id}/history` as a vertical timeline (`from → to, by whom, at when, comment`) — **this modal is your state-machine proof; don't skip it.**

## 7. Seed data (3:20–3:35) — `SeedDataService implements CommandLineRunner`

```
Team 1 (id=1):
  Priya  MANAGER   team=1  joined 2019-01-01
  Arjun  EMPLOYEE  team=1  manager=Priya joined 2020-01-01
  Meera  EMPLOYEE  team=1  manager=Priya joined 2021-01-01
  Kiran  EMPLOYEE  team=1  manager=Priya joined 2026-09-15   ← mid-year joiner (6.7 days)
  Ravi   EMPLOYEE  team=1  manager=Priya joined 2022-01-01
HR:  Divya  HR  (no team, manager=null)
Leave types: ANNUAL=20, SICK=10, CASUAL=8
Pre-seeded leaves (status=APPROVED):
  Arjun  2026-10-05 → 2026-10-09
  Meera  2026-10-06 → 2026-10-08        ← overlap window for the conflict demo
```
Optional: `data.sql` instead of the runner — but the runner handles IDs cleanly.

## 8. Manual QA script (3:35–4:00) — run this end-to-end twice

1. Login as **Ravi** → apply 2026-10-05 → 2026-10-09 → request appears with **CONFLICT badge** (Arjun + Meera off) yet was accepted → *flag, not reject*. ✔
2. Login as **Priya (manager)** → see Ravi's request with conflict detail → **Approve** with comment → status `PENDING_HR`. ✔
3. Login as **Divya (HR)** → **Approve** → `APPROVED`. Open History modal → full timeline: `null → PENDING_MANAGER → PENDING_HR → APPROVED`. ✔
4. As **Ravi**, apply again and as manager **Reject** → `REJECTED_BY_MANAGER`, manager-stage reject works. ✔
5. **Kiran** balance card shows **6.7 entitled** (not 20) → apply for 8 days → 400 insufficient balance. ✔
6. As **Arjun**, apply 2026-12-01 → 2026-12-04, do nothing; wait 60s → HR tab shows ESCALATION notification, request sits in HR queue with history row `PENDING_MANAGER → ESCALATED → PENDING_HR`. ✔
7. Try approving the same request twice fast / as the wrong actor → 403/409 JSON, no corruption. ✔
8. Restart the app → H2 file persists, everything still there. ✔

## 9. Judge pitch (60 seconds)

"Requests move through an explicit, validated state machine — every transition is audited. Managers who don't act within a configurable timeout get automatically escalated and the request routes to HR. Team conflicts are detected day-by-day and flagged for the approver — flagged, never auto-rejected, exactly as an org wants. Balances are pro-rated from joining date — a September joiner sees 6.7 of 20 days, and pending requests count as in-flight so nobody double-spends. Everything runs on H2 with a single command."

## 10. Time budget recap

| Clock | Milestone |
|---|---|
| 0:00–0:15 | Scaffold + properties + AppProperties |
| 0:15–0:40 | Entities, enums + ALLOWED map, repos, H2 console check |
| 0:40–1:20 | LeaveService: working days, entitlement, balance, apply |
| 1:20–2:00 | ApprovalService state machine + escalation scheduler |
| 2:00–2:20 | ConflictService + REST controllers + error handler |
| 2:20–3:20 | Frontend three tabs + history modal |
| 3:20–3:35 | Seed data |
| 3:35–4:00 | QA script, README, freeze |

**If you fall behind**: cut the team calendar, cut Spring Security entirely (already assumed), keep the history modal — it is the cheapest high-value proof.
