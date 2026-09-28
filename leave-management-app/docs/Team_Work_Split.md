# Problem 2 — Team of 4: Work Distribution & Timeline

One repo, one `main` branch, strict file ownership (only the owner edits their files — this kills merge conflicts, which are the #1 way 4-person hackathon teams lose an hour). The deep-dive doc (Problem2_Leave_App_DeepDive) is everyone's reference; each member's file list below maps to sections in it.

## Roles at a glance

| Member | Owns | One-line mission |
|---|---|---|
| **A — Foundation & Data** | Entities, enums, repos, seed data, H2 | Get the data model pushed early so B and C never wait |
| **B — Core Business Logic** | LeaveService, ConflictService, unit tests | Working days, pro-rated balances, apply flow, conflict flagging |
| **C — Workflow Engine & API** | ApprovalService, EscalationService, all controllers/DTOs | The state machine, escalation, and every REST endpoint |
| **D — Frontend, Demo & QA Lead** | index.html, README, demo script, QA checklist | The three role tabs, history modal, and the final 60-second pitch |

A and C must be your strongest backend people — they own the two scored cores (data integrity and the state machine). D needs the best eye for speed, since the UI is wide but shallow.

## Hour-by-hour per member

### Everyone together (0:00–0:15)
- A generates the project from start.spring.io, creates the repo, pushes, everyone clones.
- **Freeze the API contract together**: the REST table in the deep-dive doc (section 5) + DTO field names, exactly as written there. D codes against this contract all morning even before the backend exists — changing it later is the most expensive mistake available.
- Agree entity field names (section 3) — B and C write code against these names from minute 15.

### Member A — Foundation & Data
| Time | Task |
|---|---|
| 0:15–0:45 | All entities + enums (including the `ALLOWED` transition map in `RequestStatus`) — **push at 0:45 latest**; B and C are blocked on this |
| 0:45–1:00 | All repositories (including the two JPQL queries: `existsActiveLeaveOn`, `findByStatusAndPendingSinceBefore`) |
| 1:00–1:30 | `AppProperties` (escalation timeout, conflict threshold) + `SeedDataService` with the exact demo dataset from section 7 (mid-year joiner Kiran, overlapping approved leaves for Arjun + Meera) |
| 1:30–2:15 | Verify H2 console + seed runs; then **become helper**: fix wiring issues for B/C, build the curl smoke-test list for every endpoint |
| 2:15–4:00 | QA lead: run the 8-step manual QA script (section 8), log bugs, verify the restart-persistence test (step 8) |

### Member B — Core Business Logic
| Time | Task |
|---|---|
| 0:15–0:40 | `workingDays()` + `entitledDays()` + JUnit tests (these need no entities — pure functions; write and verify them first, they're demo-critical math) |
| 0:40–1:20 | `LeaveService.apply()`: validation, balance check counting pending requests as in-flight, request creation, initial transition row |
| 1:20–2:00 | `ConflictService.flagIfConflicted()`: day-by-day team overlap, threshold from config, flag + detail string, CONFLICT notification |
| 2:00–2:30 | Tests for apply/balance edge cases (weekend-only range → 0 days, insufficient balance → clean 400) |
| 2:30–4:00 | Fix QA bugs in own services; standby to help D wire tricky views (balance card, conflict badge) |

### Member C — Workflow Engine & API
| Time | Task |
|---|---|
| 0:15–0:50 | `ApprovalService`: `transition()` with `canTransitionTo` validation, `decide()` with stage/actor checks, `@Version` optimistic-lock handling → 409. Write against the enum contract before A's push, wire after |
| 0:50–1:20 | `EscalationService` `@Scheduled` poller: `PENDING_MANAGER` + timeout → `ESCALATED` → route to `PENDING_HR` + notification (use demo timeout of 60s) |
| 1:20–2:15 | All controllers + DTOs + `GlobalExceptionHandler`; `POST /requests`, approve/reject/cancel, history, balance, notifications, pending queues. Curl-smoke each endpoint as it lands (with A) |
| 2:15–3:00 | Endpoint hardening: wrong actor → 403, illegal transition → 409, double-approve race → 409, malformed dates → 400 |
| 3:00–4:00 | Fix QA bugs; idle time → add `GET /api/team/{id}/calendar` only if everything else is green |

### Member D — Frontend, Demo & QA
| Time | Task |
|---|---|
| 0:15–0:45 | Page shell: role tabs (Employee/Manager/HR), login dropdown from `/api/employees`, shared fetch helper with mock-JSON fallback (hardcode the seed team so you can build all morning without the backend) |
| 0:45–2:00 | Employee tab (balance card, apply form, my-requests table), Manager tab (queue with red CONFLICT badge + tooltip, approve/reject with comment prompt), HR tab (queue + escalation banner) |
| 2:00–2:30 | History timeline modal from `/history` — `from → to, by whom, when, comment` vertical timeline. **Do not cut this; it is the state-machine proof** |
| 2:30–3:00 | Swap mocks for the real API as C's endpoints land; add the 5s auto-refresh; status pills coloring |
| 3:00–3:30 | README: one-command run, 60-second architecture blurb, what's configurable, known limitations |
| 3:30–4:00 | Own the demo: rehearse the 8-step QA script as a narrative, assign who presents what, prep the 60-second pitch (section 9) |

## Hard checkpoints (whole team, 5 minutes each)

| Clock | Checkpoint | Done means |
|---|---|---|
| 0:15 | API contract frozen | D can develop independently all morning |
| 0:45 | Entities + enums pushed | B and C unblocked |
| 1:30 | Backend merge point 1 | Seed data runs, apply + approve work via curl |
| 2:15 | Backend feature-complete | All 11 endpoints curl-green, escalation fires on timeout |
| 3:00 | Frontend wired | Full flow clickable in the browser |
| 3:15 | First full QA pass | Section 8 script run end-to-end, bug list on the wall |
| 3:45 | FREEZE | No new code except QA bug fixes; demo rehearsal starts |

## Rules that save teams
1. **File ownership is absolute.** Need a change in someone else's file? Tell them, don't edit it.
2. **Push small, pull often.** Merge to `main` at every checkpoint minimum, not once at the end.
3. **Never "quickly refactor" entities after 1:00** — everyone downstream breaks.
4. If a member finishes early, they join QA — not new features.
5. At 3:45, whatever isn't working gets cut from the demo, not rushed live.
