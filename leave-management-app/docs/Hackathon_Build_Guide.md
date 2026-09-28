# Hackathon Build Guide — All Three Problem Statements (4-Hour Sprint)

Each section below covers: what the problem is really asking, full requirements (mapped from the minimum list, plus what judges expect beyond it), recommended architecture, data model, core algorithms, a time-boxed 4-hour build plan, and a demo script. All three are rated HARD mainly because of **state/time-based logic** (rolling windows, state machines, retry timing) — not UI volume. Spend your time on that logic and its visibility on the dashboard.

---

# Problem 1 — Python: Real-Time Log Anomaly Detector with Alert Feed

## What it's really asking
A service that tails a log file as it grows, computes error statistics over a sliding window, learns what "normal" looks like, and raises severity-tagged alerts when behavior deviates — surfaced live on a web dashboard and pushed to AWS (CloudWatch Logs / SNS).

## Recommended stack
| Layer | Choice | Why |
|---|---|---|
| Backend | FastAPI (Python 3.11+) | Native WebSocket + REST polling from one app; async file tailing is clean |
| Log tailing | Pure Python tail loop with persistent byte offset | Survives restarts, no OS quirks |
| Frontend | Single HTML + JS page served by FastAPI, Chart.js for the live chart, native WebSocket client | No build step, works out of the box |
| AWS | boto3 (`logs` + `sns` clients) behind an adapter with a mock mode | Demo works offline; real calls shown if AWS creds exist |
| Test/demo | A log generator script + pytest for the analytics | You need controllable anomalies for the demo |

## Functional requirements (checklist)
1. **Monitor a continuously growing log file** — follow a file like `tail -f`; keep a byte offset (e.g., in a small `.state` file) so restart resumes where it left off. Handle the file being appended; treat malformed lines as UNPARSED but still counted in totals.
2. **Sliding-window error rate** — maintain a `deque` of `(timestamp, level)` events; window = last N seconds (default 60s) or last N lines (time-based is better for the demo). Error rate = ERROR / (all parsed) in the window, computed every ~1s tick.
3. **Baseline for normal behavior** — collect error-rate samples during a learning phase (e.g., first 2 minutes or first 300 samples), then baseline = mean µ and std σ of the rate. Recompute periodically or use EWMA; document which you chose.
4. **Deviation detection** — compute z-score = (rate − µ) / σ each tick. Anomaly when z ≥ threshold (default 3) sustained for ≥ 2 consecutive ticks (kills single-tick noise).
5. **Severity levels** — map magnitude to severity, e.g.: z ≥ 3 → WARNING; z ≥ 5 or rate ≥ 4× baseline → CRITICAL; rate ≥ 0.8 (80% errors) or z ≥ 8 → EMERGENCY. Include anomaly summary (current rate, baseline, window size, top error messages) in the alert payload.
6. **Real-time frontend** — WebSocket endpoint `/ws/alerts` (push each alert + a stats heartbeat); ALSO expose `GET /api/alerts?since=<id>` and `GET /api/stats` so polling is demonstrably supported (the statement says "WebSockets or polling" — showing both is a cheap win).
7. **Alert feed UI** — reverse-chronological list: severity badge, timestamp, rate vs baseline, message. Plus a live line chart of error rate with the baseline band (µ ± 3σ) drawn as a shaded region — this one chart is your whole pitch.
8. **AWS push** — adapter interface `AlertSink` with two implementations: `CloudWatchSink` (boto3 `logs.put_log_events` to a configured log group/stream) and `SnsSink` (boto3 `sns.publish` to a topic ARN). If no credentials/ARN in env → fall back to `MockSink` that logs locally. Enable via `SINK=cloudwatch|sns|mock`.

### Beyond the minimum (judges notice these)
- **Alert de-duplication / cooldown**: fire an alert on *transition into* anomaly state, re-alert on severity increase, and at most every 30s while the anomaly persists. A dashboard spammed with 50 identical alerts looks broken.
- **RECOVERY event**: an "error rate back to normal" alert is very impressive and trivial to add.
- **Log generator with scenarios**: `normal` (5% errors), `burst` (60% errors for 30s), `spike` (100% for 5s), `warning-storm`. This is what makes your demo deterministic.

## Architecture
```
log_generator.py ──writes──▶ app.log
                                │
                     tailer.py (byte-offset follow, regex parse)
                                │ events
                     analytics.py  ┌─ deque (60s sliding window)
                                   ├─ baseline (µ, σ, learning phase)
                                   ├─ z-score → anomaly + severity
                                   └─ cooldown / state transitions
                                │ alerts
                     store.py (in-memory list + SQLite optional)
                                │
        FastAPI app ── /ws/alerts (WebSocket push)
                     ── /api/alerts, /api/stats (polling)
                     ── /  (serves dashboard.html + Chart.js)
                                │
                     sinks.py ── CloudWatch / SNS / Mock adapter
```
Log line format (keep it one regex): `2026-09-28T12:00:01.123Z ERROR [auth-service] Login failed for user=42`

## Data model (in-memory + optional SQLite)
- `LogEvent(ts, level, service, message)`
- `WindowStats(ts, total, errors, error_rate)`
- `Alert(id, ts, type=ANOMALY|RECOVERY, severity, error_rate, baseline_rate, z_score, window_seconds, sample_count, top_messages[])`

## Core algorithm (the part to get right)
```python
from collections import deque
import statistics, time

WINDOW = 60          # seconds
Z_THRESHOLD = 3.0
LEARN_MIN_SAMPLES = 120

class Detector:
    def __init__(self):
        self.events = deque()          # (ts, is_error)
        self.baseline_samples = []     # error rates during learning
        self.mu = self.sigma = None
        self.anomaly = False

    def tick(self, now):
        while self.events and now - self.events[0][0] > WINDOW:
            self.events.popleft()
        total = len(self.events)
        errors = sum(1 for _, e in self.events if e)
        rate = errors / total if total else 0.0

        if self.mu is None:
            self.baseline_samples.append(rate)
            if len(self.baseline_samples) >= LEARN_MIN_SAMPLES:
                self.mu = statistics.mean(self.baseline_samples)
                self.sigma = statistics.stdev(self.baseline_samples) or 1e-6
            return None, rate

        z = (rate - self.mu) / self.sigma
        is_anomalous = z >= Z_THRESHOLD
        # state transition + cooldown logic lives here
        return z, rate
```

## 4-hour plan
| Time | Task |
|---|---|
| 0:00–0:20 | Repo, venv, FastAPI skeleton serving a placeholder page. Fix the log line regex + write `log_generator.py` (normal/burst/spike modes) |
| 0:20–0:50 | Tailer with byte offset + parser + `deque` window + rolling rate printed to console (verify with generator running) |
| 0:50–1:30 | Baseline learning, z-score, severity mapping, cooldown/transition logic. Write 5 pytest unit tests for `Detector` (feed synthetic sequences — this is your differentiator) |
| 1:30–2:10 | WebSocket endpoint, alert store, REST endpoints (`/api/alerts`, `/api/stats`) |
| 2:10–3:00 | Dashboard: alert feed with severity colors + Chart.js live error-rate chart with baseline band; 2s polling fallback toggle |
| 3:00–3:30 | boto3 CloudWatch + SNS sinks behind env-config; mock mode |
| 3:30–4:00 | Full end-to-end run of the demo script below, README with run commands, screenshot for submission |

## Demo script (rehearse this)
1. Start app + generator in `normal` mode → dashboard shows rate hugging the baseline band, zero alerts.
2. Switch generator to `burst` → within ~2 window ticks an alert lands: WARNING badge, then CRITICAL as it climbs. Point at z-score on the chart.
3. Switch back to normal → RECOVERY alert, rate returns inside band.
4. Show `/api/alerts?since=` polling works, then the boto3 sink code + the mock-mode env switch.

---

# Problem 2 — Java: Leave Management App with Approval Chains

## What it's really asking
A workflow engine for leave: requests walk an explicit Manager → HR approval state machine, with timeout-based escalation, team-conflict *flagging* (not rejection), and correct pro-rated balances for mid-year joiners. The state machine and the date math are the scored core.

## Recommended stack
| Layer | Choice | Why |
|---|---|---|
| Backend | Spring Boot 3.x (Java 17+), Spring Web + Spring Data JPA + Validation | Standard, fast to scaffold from start.spring.io |
| DB | H2 (file-based) | Zero setup; judges can run it anywhere. Entity model maps 1:1 to Postgres if you switch |
| Scheduling | `@Scheduled` poller | Escalation timeouts without extra infra |
| Auth/roles | Role passed as header/request param or a trivial in-memory login | Full Spring Security burns an hour you don't have; "role-based" means the *interfaces* behave per role |
| Frontend | One HTML+JS page (or Thymeleaf) with a role switcher: Employee / Manager / HR tabs calling REST | Three "interfaces" without three codebases |

## Functional requirements (checklist)
1. **Spring Boot backend** exposing REST APIs (JSON) for all actions below.
2. **Multi-step approval workflow**: Manager approval first, then HR approval; either can reject; employee can cancel while not finally approved.
3. **Explicit state machine** — statuses as an enum with **legal transitions validated in code**, not scattered if/else:
   `DRAFT → PENDING_MANAGER → PENDING_HR → APPROVED`
   `PENDING_MANAGER → REJECTED_BY_MANAGER`, `PENDING_HR → REJECTED_BY_HR`
   `PENDING_* → CANCELLED_BY_EMPLOYEE`
   `PENDING_MANAGER → ESCALATED` (on timeout; see #5)
   Persist every transition as an audit row (who, from, to, when, comment). Illegal transition → 409 with a clear message.
4. **Automatic escalation after timeout**: `@Scheduled(fixedDelay=30s)` scans for `PENDING_MANAGER` requests older than T (configurable, e.g., 2 minutes for the demo — "days" in production); escalates: mark `ESCALATED`, log a notification (in-app notification entity or a mock email log), and route the request directly to HR (`PENDING_HR`) so it's still decided.
5. **Team-wide leave conflict detection**: when a request is created or approved, query the employee's team for date-overlapping leaves (status in `PENDING_*` or `APPROVED`). If overlapping headcount ≥ threshold (configurable, e.g., max 2 simultaneous or >30% of team), set a `conflictFlag` + `conflictDetail` on the request (list who overlaps and which days). **Flag only — never auto-reject.** The flag is visible to Manager and HR screens before they decide.
6. **Pro-rated leave balance**: on a leave request for year Y, entitled days = annual_quota × (months_remaining_in_year / 12) computed from `joiningDate`, rounded half-up. e.g., 20-day quota, joins 15-Sep → 20 × 3.5/12 ≈ 6 days for that year; a Jan-1 joiner gets the full 20. Balance = entitled − (approved + pending-in-flight days, so concurrent requests can't double-spend). Reject at apply-time only for insufficient balance.
7. **Role-based frontend** — Employee view: apply form, my requests with live status + timeline, my balance card. Manager view: pending approvals queue with conflict flags + team calendar strip. HR view: second-stage approvals, escalation queue, override view of everyone.
8. Working-day calculation: leave days = calendar days between start/end excluding weekends (optional: holiday table — mention it, don't build it if short on time).

### Beyond the minimum
- **Transition history endpoint** `GET /requests/{id}/history` powering a visible audit timeline — this *proves* the state machine.
- **Idempotent approve/reject** (double-click safe).
- Optimistic locking (`@Version`) on the request row; mention race handling for two approvers.

## Data model
- `Employee(id, name, email, role: EMPLOYEE|MANAGER|HR, managerId, teamId, joiningDate)`
- `LeaveType(id, code: ANNUAL|SICK|CASUAL, annualQuotaDays)`
- `LeaveRequest(id, employeeId, leaveType, startDate, endDate, days, reason, status, conflictFlag, conflictDetail, createdAt, pendingSince, @Version)`
- `LeaveTransition(id, requestId, fromStatus, toStatus, actorId, comment, createdAt)` — the audit log
- `Notification(id, requestId, type: ESCALATION|APPROVED|REJECTED|CONFLICT, message, createdAt, read)` — escalation "emails" land here
- Balance is computed, not stored: `entitled(joiningDate, year, quota) − SUM(days of non-rejected requests in year)`.

## Core logic snippets
```java
// explicit transitions
private static final Map<Status, Set<Status>> ALLOWED = Map.of(
    PENDING_MANAGER, Set.of(PENDING_HR, REJECTED_BY_MANAGER, CANCELLED, ESCALATED),
    PENDING_HR,      Set.of(APPROVED, REJECTED_BY_HR, CANCELLED),
    ESCALATED,       Set.of(PENDING_HR)   // escalation routes to HR
);

// pro-rated entitlement
static BigDecimal entitledDays(LocalDate joining, int quota, Year year) {
    if (!joining.getYear().equals(year.getValue())) return BigDecimal.valueOf(quota);
    YearMonth endOfYear = YearMonth.of(year.getValue(), 12);
    YearMonth joinMonth = YearMonth.from(joining);
    long monthsRemaining = ChronoUnit.MONTHS.between(joinMonth, endOfYear) + 1; // inclusive
    return BigDecimal.valueOf(quota)
            .multiply(BigDecimal.valueOf(monthsRemaining))
            .divide(BigDecimal.valueOf(12), 1, RoundingMode.HALF_UP);
}
```
Escalation poller: `@Scheduled(fixedDelay = 30000)` → `UPDATE ... WHERE status='PENDING_MANAGER' AND pendingSince < now() - T` inside a transaction; each hit writes a transition row + notification and moves status to `ESCALATED` → `PENDING_HR`.

## 4-hour plan
| Time | Task |
|---|---|
| 0:00–0:25 | start.spring.io scaffold (Web, JPA, H2, Validation, Lombok); entities + repos; H2 console working |
| 0:25–1:00 | Service layer: apply-request validation (dates, working-day count, balance check incl. in-flight), pro-rated entitlement |
| 1:00–1:40 | State machine transition service + approve/reject/cancel endpoints + transition audit rows |
| 1:40–2:10 | `@Scheduled` escalation poller + notifications |
| 2:10–2:40 | Conflict detection query + flagging |
| 2:40–3:30 | Frontend: three role tabs hitting the REST API (bootstrap table is fine) |
| 3:30–4:00 | Seed data + demo run + README |

## Demo script + seed data you must prepare
Seed: a team of 6 (1 manager, 1 HR, 4 employees), one employee with `joiningDate = 2026-09-15` (pro-rating star), two employees already approved for overlapping dates.
1. Employee applies for leave → shows in Manager queue with **conflict flag** (two teammates already off) — approve anyway to show "flag, not reject".
2. HR approves → status timeline shows all transitions.
3. Show the mid-year joiner's balance card (≈6 days, not 20) and apply for 8 → rejected for insufficient balance.
4. Apply a fresh request, do NOT act as manager, wait out the demo timeout (set it to 60s) → escalation notification appears, request routes to HR queue.
5. Show the transition-history endpoint — the state machine, proven.

---

# Problem 3 — .NET: Background Job Processor with Job Status Dashboard

## What it's really asking
A durable job queue inside ASP.NET Core: jobs are enqueued via API, processed by a `BackgroundService`, transient failures retry with exponential backoff, permanent failures (after N retries) land in dead-letter storage, and a dashboard shows live state.

## Recommended stack
| Layer | Choice | Why |
|---|---|---|
| Backend | ASP.NET Core 8 (Web API) | Native `BackgroundService` |
| Persistence | EF Core + SQLite | The statement explicitly requires persisting job + dead-letter info |
| Frontend | Blazor Server (single solution, auto-refresh trivially) — or React+Vite if the team prefers | Statement says "React or Blazor" |
| Live updates | Polling every 2s (simplest) or a touch of SignalR | Dashboard must feel live |

## Functional requirements (checklist)
1. **ASP.NET Core backend** — Web API project.
2. **Background processing** via a class deriving from `BackgroundService` overriding `ExecuteAsync`: a long-running loop that claims due jobs and processes them.
3. **Job queue/processing mechanism** — DB-backed queue (the Jobs table) + in-process worker loop. Claim logic: `SELECT ... WHERE Status=Queued AND NextAttemptAt <= now` wrapped in a transaction (or with `ExecuteUpdate` atomic claim) so it's multi-worker safe; add a `SemaphoreSlim` for parallel execution if time permits. Jobs are **not** lost on restart because state lives in SQLite.
4. **Retry failed jobs** — on handler exception: `Attempts++`, `Status=Retrying`, `NextAttemptAt = now + backoff`.
5. **Exponential backoff** — `delay = min(BaseSeconds × 2^(Attempts−1), MaxCap)` **with jitter** (±20%): e.g., base 2s → 2s, 4s, 8s, 16s, capped at 60s. Base, cap, and max retries from `appsettings.json` (configurable) with per-job override.
6. **Configurable maximum retry count** — default 5; when `Attempts >= MaxRetries` → `Status=DeadLettered`, moved/recorded in dead-letter storage.
7. **Dead-letter storage** — a `DeadLetter` entity (or Jobs with terminal status + a dedicated view) capturing full context: payload, attempts, last error + stack trace, timestamps, correlation id. Persisted in SQLite.
8. **Status API** — `POST /jobs` (enqueue, returns id), `GET /jobs` (filter by status/type), `GET /jobs/{id}` (full attempt history), `GET /jobs/deadletter`, `POST /jobs/{id}/replay` (requeue a dead-lettered job with attempts reset — huge demo value).
9. **Dashboard** — live table: id, type, status badge (Queued/Running/Retrying/Succeeded/Failed/DeadLettered), attempts/max, next attempt countdown, age; click a row → failure details (error, stack, attempt timeline); dead-letter tab with replay button.
10. **Persist job and dead-letter information** — EF Core + SQLite, restart the app mid-demo to prove durability.

### Beyond the minimum
- **Pluggable job handlers** keyed by `Job.Type` — `IJobHandler` resolved from DI. Ship three demo handlers:
  - `EchoJob` (always succeeds)
  - `FlakyEmailJob` (fails ~50% — shows retry + backoff beautifully on the dashboard)
  - `ExplodeJob` (always throws — shows the full journey to dead-letter)
- **Attempt history table** (`JobAttempt`: jobId, attemptNo, startedAt, finishedAt, error) → powers the retry timeline on the dashboard.
- Cancellation-aware handler loop (`CancellationToken` from `ExecuteAsync`).
- Note in README how this maps to production concerns (visibility timeout, poison messages, outbox).

## Architecture
```
POST /jobs ──▶ JobsController ──▶ EF Core ──▶ SQLite (jobs, job_attempts, dead_letters)
                                                ▲
                    BackgroundService.ExecuteAsync│ claim due jobs (Queued & NextAttemptAt<=now)
                                                │
                     JobHandlerRegistry ──▶ IJobHandler implementations
                                                │
                     on success: Succeeded, CompletedAt
                     on failure: Attempts++, Retrying, NextAttemptAt=now+backoff
                     on Attempts>=MaxRetries: DeadLettered + dead_letter row
                                                │
Dashboard (Blazor/React, polls /jobs + SignalR-ish refresh) ◀── GET endpoints
```

## Data model (EF Core)
- `Job(Id, Type, Payload(json), Status: Queued|Running|Retrying|Succeeded|DeadLettered, Attempts, MaxAttempts, NextAttemptAt, CreatedAt, CompletedAt, LastError)`
- `JobAttempt(Id, JobId, AttemptNo, StartedAt, FinishedAt, ErrorMessage, StackTrace)`
- `DeadLetter(Id, JobId, Type, Payload, Attempts, FinalError, DeadLetteredAt, CorrelationId, ReplayJobId?)`

## Core loop sketch
```csharp
public class Worker : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken ct)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(1));
        while (await timer.WaitForNextTickAsync(ct))
        {
            var claimed = await db.Jobs
                .Where(j => j.Status == JobStatus.Queued && j.NextAttemptAt <= DateTime.UtcNow)
                .OrderBy(j => j.NextAttemptAt)
                .Take(batchSize)
                .ExecuteUpdateAsync(... /* atomically set Running */, ct);
            foreach (var job in /* fetched claimed rows */)
            {
                var delay = TimeSpan.FromSeconds(Math.Min(
                    _opts.BaseBackoffSeconds * Math.Pow(2, job.Attempts - 1),
                    _opts.MaxBackoffSeconds));
                // jitter: delay * (0.8 + rand*0.4)
                try { await handler.ExecuteAsync(job.Payload, ct);
                      job.Status = Succeeded; }
                catch (Exception ex) {
                    job.Attempts++; job.LastError = ex.Message;
                    if (job.Attempts >= job.MaxAttempts) { → dead-letter }
                    else { job.Status = Retrying; job.NextAttemptAt = now + jittered; }
                }
            }
            await db.SaveChangesAsync(ct);
        }
    }
}
```

## 4-hour plan
| Time | Task |
|---|---|
| 0:00–0:25 | Solution scaffold; EF Core entities + SQLite + `EnsureCreated`/migration; options pattern for backoff config |
| 0:25–1:00 | `POST /jobs` enqueue + `JobHandlerRegistry` + the three demo handlers |
| 1:00–2:00 | `BackgroundService` worker: claim, execute, retry with backoff, dead-letter + attempt-history rows |
| 2:00–2:30 | Status endpoints incl. dead-letter list + replay |
| 2:30–3:30 | Dashboard: live table + job details drawer + dead-letter tab with replay |
| 3:30–4:00 | Demo run (below), README, kill-and-restart durability test |

## Demo script
1. Enqueue 3 Echo + 3 Flaky + 1 Explode from the dashboard/API.
2. Watch the table: Flaky ones flip Retrying → next-attempt countdown (2s → 4s → 8s visible on the details drawer), most eventually Succeed.
3. Explode walks Queued → Running → Retrying ×5 → **DeadLettered** with full error + 5-attempt timeline. Open the dead-letter tab, hit Replay → it lives again and dies again (or use Flaky and it succeeds — even better).
4. **Kill the app while jobs are Retrying, restart** → they resume from SQLite. This is the durability money-shot.

---

# Cross-cutting advice (applies to all three)

- **Score the core algorithm first.** Each problem has one scored heart: z-score anomaly detection (P1), state machine + pro-rating (P2), retry/backoff/dead-letter (P3). Build and unit-test that before any UI. A working algorithm with an ugly UI beats a beautiful dashboard wrapped around broken logic.
- **Deterministic demo beats feature count.** Every problem above has a scripted demo because judges remember what they *saw working*.
- **README is a scored artifact**: run commands (one-liner), 60-second architecture diagram, what's configurable, known limitations stated honestly.
- **Unit tests on the core logic** — 5–10 focused tests, run them in your demo if time allows.
- **Time discipline**: at T-40min stop building, freeze features, run the demo end-to-end twice, write the README.
- **Choice guidance** if you're torn: strongest-in-Python-with-FastAPI → P1; strongest-in-Spring → P2; strongest-in-.NET → P3. All three have near-identical shape (backend + one dashboard), so pick by stack fluency, not by problem.
