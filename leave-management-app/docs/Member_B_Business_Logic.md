# MEMBER B — Core Business Logic
## Leave Management App | Java + Spring Boot | 4-person parallel build via Antigravity + GitHub

You own the "business math" judges score hardest: working-day counts, **pro-rated leave balances for mid-year joiners**, the apply flow, and **team conflict flagging (flag, never auto-reject)**.

---

## YOUR OWNERSHIP (only you edit these)
```
src/main/java/com/hackathon/leave/service/LeaveService.java
src/main/java/com/hackathon/leave/service/ConflictService.java
src/main/java/com/hackathon/leave/service/dto/BalanceSummary.java
src/test/java/com/hackathon/leave/...  (all test files)
```
Everything else belongs to A, C, or D. Need a change elsewhere? Message the owner.

## SHARED CONTRACT (fixed agreement — your code must compile against these signatures)
- Base package `com.hackathon.leave`. Java 17, Maven, no Lombok.
- **Enums:** `Role {EMPLOYEE, MANAGER, HR}`; `RequestStatus {PENDING_MANAGER, PENDING_HR, APPROVED, REJECTED_BY_MANAGER, REJECTED_BY_HR, CANCELLED, ESCALATED}`.
- **Entities (A owns, you consume — exact field names):**
  - `Employee(Long id, String name, String email, Role role, Long managerId, Long teamId, LocalDate joiningDate)`
  - `LeaveType(Long id, String code, int annualQuotaDays)`
  - `LeaveRequest(Long id, Long employeeId, Long leaveTypeId, LocalDate startDate, LocalDate endDate, int days, String reason, RequestStatus status, boolean conflictFlag, String conflictDetail, Instant createdAt, Instant pendingSince, Long version)`
  - Repos: `EmployeeRepository.findByTeamId`, `LeaveTypeRepository.findByCode`, `LeaveRequestRepository.findByEmployeeId`, `LeaveRequestRepository.countActiveOn(empId, day)` (counts APPROVED + PENDING + ESCALATED leaves covering `day`).
- **Method signatures other members call (must match exactly):**
  - `LeaveService: public LeaveRequest apply(Long employeeId, String leaveTypeCode, LocalDate start, LocalDate end, String reason)`
  - `LeaveService: public BalanceSummary getBalance(Long employeeId, int year)`
  - `ConflictService: public void flagIfConflicted(LeaveRequest request)`
  - `record BalanceSummary(int year, BigDecimal entitledDays, int usedDays, BigDecimal remainingDays)`
- `AppProperties.conflict.maxSimultaneous` (int) is your conflict threshold — read from config, never hardcode.
- You may call `ApprovalService.recordTransition(Long requestId, Long actorId, RequestStatus from, RequestStatus to, String comment)` (C's file) for the initial `null → PENDING_MANAGER` transition.

## WHAT TO BUILD

### 1. Pure functions first — no dependencies needed (0:15–0:40)
```java
static int workingDays(LocalDate start, LocalDate end) {          // in LeaveService
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
// Kiran joined 2026-09-15, quota 20 → 20 × 4/12 = 6.7 days
```
Write JUnit tests NOW for these (they need no entities): full-year joiner → 20.0; Sept 15 joiner → 6.7; Dec joiner → 1.7; weekend-only range → 0 days. These tests are demo gold.

### 2. `apply()` (0:40–1:20)
Flow, all inside one `@Transactional` method:
1. Validate: `end >= start`, dates in the future, leave type code exists. Invalid → throw your team's `ApiException(400, msg)` (a tiny class you may create inside your own package, or reuse C's if merged).
2. `days = workingDays(start, end)`; if 0 → 400 "weekend-only range".
3. **Balance check** (see 3): `days > remaining` → 400 "insufficient balance".
4. Create `LeaveRequest`: status `PENDING_MANAGER`, `createdAt = pendingSince = Instant.now()`, save.
5. Call `conflictService.flagIfConflicted(request)` — **flag only, never reject**.
6. `recordTransition(requestId, employeeId, null, PENDING_MANAGER, "applied")`.
7. Return the saved entity.

### 3. `getBalance()` — computed, never stored (1:20 onwards)
```java
entitled = entitledDays(emp.getJoiningDate(), leaveType("ANNUAL").getAnnualQuotaDays(), year);
used = sum of days of the employee's requests this year with status in
       {APPROVED, PENDING_MANAGER, PENDING_HR, ESCALATED};   // pending = "in flight"
remaining = entitled - used;
```
Pending counting as in-flight is deliberate: two simultaneous applies can't double-spend the same days. Say this to the judges.

### 4. `ConflictService.flagIfConflicted()` (1:20–2:00)
```java
for each day in request range:
    teammates = employeeRepo.findByTeamId(emp.getTeamId()) excluding the requester
    offToday  = teammates where leaveRequestRepo.countActiveOn(teammate.getId(), day) > 0
    if offToday.size() >= appProperties.getConflict().getMaxSimultaneous():
        record that day + who is off
if any days over limit:
    request.setConflictFlag(true);
    request.setConflictDetail("Team conflict on N day(s): 2026-10-06 → off: [Arjun, Meera]; ...");
```
Also write a **CONFLICT notification row** for the manager (Notification entity, type `CONFLICT`). Flag — do not change status. Never reject.

### 5. Unit tests for apply/balance (2:00–2:30)
Weekend-only range → 400; insufficient balance (Kiran asks 8 days, entitled 6.7) → 400; conflict path sets flag but keeps status PENDING_MANAGER.

## ANTIGRAVITY KICKOFF PROMPT (paste into your agent)
```
You are building the business-logic layer of a Spring Boot 3.3 (Java 17, no Lombok)
leave-management app. Build ONLY: service/LeaveService.java, service/ConflictService.java,
service/dto/BalanceSummary.java, and unit tests under src/test.
Dependencies (do NOT create them for real — create STUBS in package
com.hackathon.leave.stubs, each stub file starting with a // DELETE-BEFORE-INTEGRATION
comment, matching these shapes exactly):
[paste the SHARED CONTRACT: entities, repos, signatures, AppProperties, ApiException]
Build these methods exactly: [paste WHAT TO BUILD sections 1-4].
Add JUnit 5 tests for workingDays, entitledDays, balance edge cases and conflict flagging.
Rules: pro-rated entitlement = quota × (months remaining in year incl. join month)/12,
rounded HALF_UP to 1 decimal. Balance counts APPROVED+PENDING+ESCALATED as used.
Conflicts FLAG the request (conflictFlag + conflictDetail + CONFLICT notification),
never reject it. Verify with `mvn test` — all tests must pass.
```

## THE STUB STRATEGY (how you work before A pushes)
A pushes entities/repos to `main` around 0:45. Until then: let Antigravity create minimal stub classes (same names, fields, and method signatures as the contract) under `com.hackathon.leave.stubs`, each marked `// DELETE-BEFORE-INTEGRATION`. Your tests run against stubs. When A pushes: `git pull`, delete the stubs package, fix imports to the real classes, re-run `mvn test`. Budget 15 minutes for this swap and do it immediately at the checkpoint.

## VERIFY BEFORE PUSHING (acceptance checklist)
- [ ] `mvn test` green, including: Sept-15 joiner → 6.7 days; weekend-only → 0; Kiran 8-day request → 400
- [ ] Conflict sets flag + detail + notification, but status stays PENDING_MANAGER
- [ ] Signatures match the contract exactly (C's controllers call you)
- [ ] No files created outside your ownership list (except marked stubs)

## GIT DISCIPLINE
Branch: `feature/business` off `main`. Commit after every green test run. Merge to `main` at the ~2:00 checkpoint, immediately after pulling A's foundation merge. Never edit A's, C's, or D's files.
