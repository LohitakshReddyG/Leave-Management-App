# MEMBER A — Foundation & Data Layer
## Leave Management App | Java + Spring Boot | 4-person parallel build via Antigravity + GitHub

You are the foundation. Everyone else builds against your entities, enums, and repositories, so your #1 job is: **push the contract layer to GitHub fast (by the 45-minute mark)**, then seed data, then QA lead.

---

## YOUR OWNERSHIP (only you edit these)
```
pom.xml
.gitignore
src/main/resources/application.properties
src/main/java/com/hackathon/leave/LeaveApplication.java
src/main/java/com/hackathon/leave/model/Role.java
src/main/java/com/hackathon/leave/model/RequestStatus.java
src/main/java/com/hackathon/leave/model/Employee.java
src/main/java/com/hackathon/leave/model/LeaveType.java
src/main/java/com/hackathon/leave/model/LeaveRequest.java
src/main/java/com/hackathon/leave/model/LeaveTransition.java
src/main/java/com/hackathon/leave/model/Notification.java
src/main/java/com/hackathon/leave/repo/*.java  (5 repositories)
src/main/java/com/hackathon/leave/config/AppProperties.java
src/main/java/com/hackathon/leave/seed/SeedDataService.java
```
Everything else belongs to B, C, or D. If you need a change in their files, message them — do not edit.

## STEP 0 — Repo setup (first 10 minutes, you drive)
1. Create GitHub repo `leave-management-app` (private), add the other 3 as collaborators.
2. Generate the skeleton at start.spring.io: **Java 17, Maven, Spring Boot 3.3.x**, dependencies: Spring Web, Spring Data JPA, H2, Validation. **NO Lombok** — plain getters/setters/constructors only (avoids annotation-processing merge issues later).
3. Push `main` with a clean skeleton that compiles (`mvn compile` passes), then post the clone URL in the team chat. The other 3 are waiting on this.
4. `.gitignore` must contain: `target/`, `*.db`, `.idea/`, `.vscode/`, `*.iml`, `.env`.

## SHARED CONTRACT (fixed agreement with B, C, D — do not deviate)
- Base package: `com.hackathon.leave`. Main class `LeaveApplication` annotated `@SpringBootApplication @EnableScheduling`.
- H2 file DB: `jdbc:h2:file:./leavedb`, `spring.jpa.hibernate.ddl-auto=update`, H2 console at `/h2-console`.
- Config keys bound by `AppProperties` (`@ConfigurationProperties(prefix="app")`, nested static classes `Escalation(long timeoutSeconds)` and `Conflict(int maxSimultaneous)`): `app.escalation.timeout-seconds=60`, `app.conflict.max-simultaneous=2`.
- **Enums (exact values):** `Role { EMPLOYEE, MANAGER, HR }` and `RequestStatus { PENDING_MANAGER, PENDING_HR, APPROVED, REJECTED_BY_MANAGER, REJECTED_BY_HR, CANCELLED, ESCALATED }`.
- **Transition map (the state machine, lives in RequestStatus):**
  - PENDING_MANAGER → {PENDING_HR, REJECTED_BY_MANAGER, CANCELLED, ESCALATED}
  - PENDING_HR → {APPROVED, REJECTED_BY_HR, CANCELLED}
  - ESCALATED → {PENDING_HR}
  - APPROVED, REJECTED_BY_MANAGER, REJECTED_BY_HR, CANCELLED → terminal (empty set)
  - Method: `public boolean canTransitionTo(RequestStatus target)`
- **Entity fields (exact names):**
  - `Employee`: Long id, String name, String email, Role role, Long managerId, Long teamId, LocalDate joiningDate
  - `LeaveType`: Long id, String code, int annualQuotaDays
  - `LeaveRequest`: Long id, Long employeeId, Long leaveTypeId, LocalDate startDate, LocalDate endDate, int days, String reason, RequestStatus status, boolean conflictFlag, String conflictDetail, Instant createdAt, Instant pendingSince, `@Version Long version`
  - `LeaveTransition`: Long id, Long requestId, Long actorId, RequestStatus fromStatus, RequestStatus toStatus, String comment, Instant at
  - `Notification`: Long id, Long requestId, String type, String message, Instant createdAt
  - All enums persisted with `@Enumerated(EnumType.STRING)`.

## WHAT TO BUILD

### 1. The six model classes above (0:15–0:45)
Use the field lists verbatim. Include the transition map inside `RequestStatus` exactly as specified. Push to `main` **at 0:45 at the latest** — B and C are coding against this contract with stubs and will swap stubs for your classes the moment you push. Late push = the whole team stalls.

### 2. Five repositories (0:45–1:00)
```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByTeamId(Long teamId);
}
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    LeaveType findByCode(String code);
}
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeId(Long employeeId);
    List<LeaveRequest> findByStatusAndPendingSinceBefore(RequestStatus s, Instant t);
    // + JPQL used by B:
    @Query("SELECT COUNT(r) FROM LeaveRequest r WHERE r.employeeId = :empId AND r.status IN
           (com.hackathon.leave.model.RequestStatus.PENDING_MANAGER, ...PENDING_HR, ...ESCALATED, ...APPROVED)
           AND r.startDate <= :day AND r.endDate >= :day")
    long countActiveOn(@Param("empId") Long empId, @Param("day") LocalDate day);
}
public interface LeaveTransitionRepository extends JpaRepository<LeaveTransition, Long> {
    List<LeaveTransition> findByRequestIdOrderByAtAsc(Long requestId);
}
public interface NotificationRepository extends JpaRepository<Notification, Long> { }
```

### 3. `AppProperties` + `application.properties` (1:00–1:10)
As in the contract above. This is what makes "configurable timeout" and "configurable conflict threshold" true.

### 4. `SeedDataService implements CommandLineRunner` (1:10–1:40)
Exact dataset (demo-critical, do not improvise):
```
Team 1 (teamId=1), Leave types: ANNUAL(20), SICK(10), CASUAL(8)
  1  Priya  MANAGER  team=1  managerId=null   joined 2019-01-01
  2  Arjun  EMPLOYEE  team=1  managerId=1      joined 2020-01-01
  3  Meera  EMPLOYEE  team=1  managerId=1      joined 2021-01-01
  4  Kiran  EMPLOYEE  team=1  managerId=1      joined 2026-09-15   ← mid-year joiner → 6.7 days
  5  Ravi   EMPLOYEE  team=1  managerId=1      joined 2022-01-01
  6  Divya  HR        team=null managerId=null  joined 2018-01-01
Pre-seeded APPROVED leaves:
  Arjun 2026-10-05 → 2026-10-09 (5 days, ANNUAL)
  Meera 2026-10-06 → 2026-10-08 (3 days, ANNUAL)   ← overlap window for conflict demo
```
Make the runner idempotent (`if (employeeRepo.count() > 0) return;`).

## ANTIGRAVITY KICKOFF PROMPT (paste into your agent)
```
You are building the foundation layer of a Spring Boot 3.3 (Java 17, Maven, no Lombok)
leave-management app. Build ONLY these files: [paste YOUR OWNERSHIP list].
Follow the SHARED CONTRACT exactly: [paste the SHARED CONTRACT section].
Rules:
- Plain getters/setters/constructors, no Lombok.
- Every enum persisted as STRING.
- RequestStatus contains the ALLOWED transition map and canTransitionTo().
- SeedDataService implements CommandLineRunner with exactly this dataset: [paste dataset].
- Seed must be idempotent.
- Do not create any controllers, services, or files outside the ownership list.
Verify with `mvn compile` and `mvn spring-boot:run`, then confirm via the H2 console
that all 6 employees, 3 leave types and 2 pre-seeded leaves exist.
```

## VERIFY BEFORE PUSHING (acceptance checklist)
- [ ] `mvn compile` clean; app starts; H2 console shows seeded rows
- [ ] Enum values and field names match the contract **character for character**
- [ ] Transition map matches the contract (get this wrong and C's logic silently breaks)
- [ ] Pushed to `main` by 0:45 (entities/repos), seed + config by 1:40

## AFTER YOUR BUILD (1:40 onward) — you are QA lead
- Build the curl smoke-test list for every endpoint in the contract (C uses it).
- At integration time you run the 8-step QA script in the Integration Guide and log bugs.
- You own `pom.xml` — if anyone needs a dependency, you add it, they don't.

## GIT DISCIPLINE
Branch: `feature/foundation`. Commit after every completed file with a clear message (`Add entities + state machine enum`, `Add repositories`, `Add seed data`). Merge to `main` at the 0:45 checkpoint (skeleton+models+repos), then again after seed. Never edit files outside your ownership list.
