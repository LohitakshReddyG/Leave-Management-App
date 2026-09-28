# INTEGRATION GUIDE — GitHub Workflow & Final Assembly
## Leave Management App | 4-person parallel build via Antigravity + GitHub

How the four individually built parts become one project: repo setup, branch rules, merge checkpoints, the swap-stubs procedure, final QA, and the demo.

---

## 1. Repo setup (first 10 minutes — Member A drives)

1. A creates the GitHub repo **`leave-management-app`** (private) and adds the other 3 as collaborators.
2. A generates the Spring Boot skeleton (Java 17, Maven, Spring Boot 3.3.x, deps: Web, Data JPA, H2, Validation, **no Lombok**), verifies `mvn compile`, pushes to `main`, shares the clone URL.
3. Everyone else:
   ```
   git clone <url> && cd leave-management-app
   git checkout -b feature/business      # A: feature/foundation
                                          # B: feature/business
                                          # C: feature/workflow-api
                                          # D: feature/frontend
   ```
4. **File ownership is absolute** (each member's doc lists their files). You never edit a file you don't own — you ask the owner. This is what makes merging painless.

## 2. How parallel building works (the stub system)

B and C can't wait for A's entities; C can't wait for B's services. So:
- Everyone codes against the **shared contract** (identical in each member file: enums, entity fields, method signatures, API paths, JSON shapes).
- Anything outside your ownership that you need → a minimal **stub** in `com.hackathon.leave.stubs`, every stub file marked `// DELETE-BEFORE-INTEGRATION`.
- At the merge checkpoints you pull `main`, delete your stubs, swap imports to the real classes, and re-verify. Budget 15–20 min per swap.
- D never needs stubs: their frontend runs on `USE_MOCKS = true` hardcoded JSON that matches the contract.

## 3. Merge checkpoints (put these times on a wall)

| Clock | What happens | Who | Definition of done |
|---|---|---|---|
| 0:10 | Skeleton repo pushed | A | `main` compiles, clone URL shared |
| 0:45 | **Merge 1 — foundation** | A merges `feature/foundation` → `main` | Entities, enums + transition map, repos, config on `main` |
| 0:50 | **Stub swap 1** | B and C: pull `main`, delete entity/repo stubs, wire real classes, re-run tests/curl | `mvn compile` + B's tests green on real model |
| 2:00 | **Merge 2 — business** | B merges `feature/business` → `main` | LeaveService, ConflictService, all unit tests on `main` |
| 2:10 | **Merge 3 — workflow/API** | C merges `feature/workflow-api` → `main` (after pulling B's merge) | All 11 endpoints live; approve/reject/escalation work via curl |
| 2:20 | **Stub swap 2** | C: delete service stubs if any remained; B: confirm `recordTransition` wiring | Full backend smoke-test green |
| 2:30 | **Merge 4 — frontend** | D merges `feature/frontend`, then flips `USE_MOCKS = false` in one final commit | Full flow works in the browser |
| 2:30–3:15 | Integration QA (§4 below) | A leads, everyone fixes their own bugs | 8-step script passes twice |
| 3:15–3:45 | Buffer for real bugs | all | — |
| 3:45 | **FREEZE** | all | Only bug fixes; demo rehearsal starts |

### Merge mechanics (keep conflicts near-zero)
- Ownership is disjoint, so merges should be clean. Standard flow per merge:
  ```
  git checkout main && git pull
  git merge feature/<name>        # or a PR on GitHub — fine too, use PRs if the team prefers
  mvn compile && mvn test
  git push
  ```
- If a conflict does appear (usually `pom.xml` or `application.properties`), the FILE OWNER resolves it — nobody else.
- One person merges at a time; announce in the team chat before pushing to `main`.

## 4. Integration QA script (run end-to-end, twice, on merged `main`)

Logins: **Priya = manager (id 1)**, **Ravi = employee (id 5)**, **Kiran = mid-year joiner (id 4)**, **Arjun = employee (id 2)**, **Divya = HR (id 6)**. App: `mvn spring-boot:run` → `http://localhost:8080`.

1. **Conflict flagging**: as Ravi, apply 2026-10-05 → 2026-10-09 → request is accepted BUT shows the red CONFLICT badge (Arjun + Meera already off). *Flag, not reject.* ✔
2. **Manager approval**: as Priya, see Ravi's request with conflict detail → Approve with comment → status `PENDING_HR`. ✔
3. **HR approval**: as Divya, approve → `APPROVED`. Open History modal → full timeline `null → PENDING_MANAGER → PENDING_HR → APPROVED` with actors and comments. ✔
4. **Rejection**: apply again as Ravi; as Priya, Reject → `REJECTED_BY_MANAGER`. ✔
5. **Pro-rating**: as Kiran, balance card shows **6.7 entitled** (not 20); apply for 8 days → clean "insufficient balance" error. ✔
6. **Escalation**: as Arjun, apply 2026-12-01 → 2026-12-04; nobody acts; within ~70s the HR tab shows an ESCALATION notification and the request sits in the HR queue with history rows `PENDING_MANAGER → ESCALATED → PENDING_HR`. ✔
7. **Guards**: approve as the wrong actor → 403 error shown; double-click approve → no corruption (409 under the hood); request a weekend-only range → clean 400. ✔
8. **Durability**: restart the app → all data still there (H2 file), the frontend picks up where it left off. ✔

Each ✔ is a slide in your demo. If any step fails, the file owner of the broken layer fixes it — never hotfix someone else's file.

## 5. Repo hygiene rules

- Branch names exactly: `feature/foundation`, `feature/business`, `feature/workflow-api`, `feature/frontend`.
- Commit small and often, message format: `who: what` — e.g. `B: pro-rated balance + tests`, `C: escalation scheduler`.
- Never force-push `main`. Never commit `leavedb` files (`.gitignore` covers `*.db`).
- The GitHub repo IS your submission artifact — make sure `main` is runnable from a clean clone at the end: `git clone … && mvn spring-boot:run` must work with zero manual steps. Test this from a fresh clone before submitting.
- If you use PRs instead of direct merges, that's fine — but the checkpoint times still hold.

## 6. Integration failure modes (what usually goes wrong — pre-check each)

| Symptom | Cause | Prevention |
|---|---|---|
| B's service won't compile after Merge 1 | Field/enum name drift from the contract | Swap stubs immediately at 0:50, don't batch it |
| Frontend shows `undefined` fields after Merge 4 | JSON key mismatch (e.g. `start` vs `startDate`) | D's contract table is exact — C's DTOs match it character-for-character |
| Escalation never fires | `@EnableScheduling` missing, or stale `leavedb` had no `pendingSince` | Delete `leavedb` files after Merge 3 so seed re-runs cleanly |
| 500 on apply after Merge 2 | B and C both wire `recordTransition` differently | B calls `ApprovalService.recordTransition(...)` — the signature is in both files |
| OptimisticLockException storms | Two `@Transactional` layers saving the same entity | Only the service that changed status saves the request |
| "Works on my machine" | Different Java versions / leftover local DB | Agree: everyone runs Java 17; integration tests happen on a FRESH clone |

## 7. Submission checklist (final 15 minutes)

- [ ] Clean clone → `mvn spring-boot:run` → all 8 QA steps pass
- [ ] `main` branch is the submission; feature branches can remain but `main` must be self-sufficient
- [ ] README on `main`: run command, demo logins, architecture summary, limitations
- [ ] Delete the `stubs` package and search the repo for `TODO` / `DELETE-BEFORE-INTEGRATION` — zero hits
- [ ] `USE_MOCKS = false` in the pushed `app.js`
- [ ] Demo rehearsed once, timed under 5 minutes, presenter split agreed
