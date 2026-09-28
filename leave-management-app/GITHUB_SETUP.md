# Pushing ClockIt to GitHub — team setup

The bundle (`leave-management-app.bundle`) carries the whole repository:
`main` (the complete, verified project) plus one branch per member with that
member's ownership scope in `WORKSTREAM.md`.

## 1. One-time: put it on GitHub

Whoever creates the repo (Member A):

```bash
git clone leave-management-app.bundle leave-management-app
cd leave-management-app

# create an EMPTY repo named leave-management-app on github.com first, then:
git remote add origin https://github.com/<your-org-or-username>/leave-management-app.git
git push -u origin --all        # pushes main + all four member branches
```

Add the other members as collaborators (repo → Settings → Collaborators).

## 2. Each member: clone and take your branch

```bash
git clone https://github.com/<your-org-or-username>/leave-management-app.git
cd leave-management-app
git config user.name  "Your Name"          # your commits carry your name
git config user.email "you@example.com"
git checkout member-a-foundation-data       # or your own branch
cat WORKSTREAM.md                           # exactly what you own — read this first
```

| Branch | Owns |
|---|---|
| `member-a-foundation-data` | entities, state-machine enum, repositories, config, seed data, pom |
| `member-b-business-logic` | LeaveService, ConflictService, unit tests |
| `member-c-workflow-api` | ApprovalService, EscalationService, controllers, DTOs |
| `member-d-frontend-demo` | the React app in `frontend/`, README, demo script |

## 3. Working rules (from the Integration Guide — the short version)

- You only edit files listed in your `WORKSTREAM.md`. Need a change elsewhere?
  Tell the owner; never edit someone else's file.
- Commit small and often: `who: what` (e.g. `B: fix pro-rating rounding`).
- Push your branch at least at the checkpoints; merge to `main` one person at
  a time, announcing it in the team chat first.
- Before merging: `git checkout main && git pull && git merge <branch>` and
  `mvn -q test` must pass.
- `main` must always be runnable from a clean clone with `mvn spring-boot:run`.

## 4. Rebuilding the frontend

Member D: after editing React code, run `npm install && npm run build` inside
`frontend/` (outputs into `src/main/resources/static/`) and commit the built
assets too — that is what keeps the one-command demo working without Node.

## 5. If the demo machine has no internet

Nothing to fear: the jar ships with the React build compiled in, and Maven
dependencies are cached after the first build. `mvn spring-boot:run` is all
you need on stage.
