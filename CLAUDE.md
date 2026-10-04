# Classly

School organization app: photograph a class schedule or agenda and it lands
in a calendar; add reminders and per-assignment notes; chat with an AI that
helps plan homework/study time and writes it to the calendar. See
`.claude/context.md` for the product overview and decisions.

## Session continuity (`.claude/context.md` and `.claude/current.md`)

- `.claude/context.md` is the stable project overview (architecture, major
  features, decisions already made).
- `.claude/current.md` holds the maintainer's active **sprint plan**: a
  checklist of tasks they've laid out in conversation, not a "last thing
  done" log. It's maintainer-authored — when they describe a plan, write it
  down as a checklist; don't add tasks to it on your own initiative.
- Don't rewrite `current.md` at the start of every task — it persists
  across tasks/sessions untouched by default. It only changes when:
  - The maintainer communicates a new or updated plan — write it down
    (replacing what's there).
  - A task gets finished — check the plan for a matching item and remove
    it if present. If the finished task isn't on the plan, leave the file
    alone; not everything has to be planned.
- If the maintainer asks you to "consult the plan," read `current.md` to
  see what's left and use it to decide what's next.
- Keep entries as a short checklist (one line per task), not a narrative
  status writeup — commit history and PR descriptions already capture the
  "what happened"; this file is just "what's still open."
- Update `context.md` (separately from the sprint plan) when a task changed
  architecture, added a major feature, or made a decision worth not
  relitigating later.
- Keep `context.md` high-level: architecture, decisions, and concrete
  config facts (IDs, regions, key/secret locations) other tasks need
  without re-deriving them. Operational gotchas — what breaks, how it bit
  us before, how to debug it — belong in this file (`CLAUDE.md`) instead.
  Don't restate `context.md`'s facts here; reference them.
- Keep both files to the "what future tasks actually need" bar, not a full
  history of how a decision was reached — if a section balloons past a
  few lines per fact, it's due for a trim.

## Workflow conventions

- After pushing commits to a feature branch, immediately open a PR against
  `main` — don't wait to be asked. The maintainer merges from the PR link.
  (The very first commit, which creates `main`'s content, is the exception —
  there is nothing to open a PR against yet.)
- Feature branches in this repo get reused across tasks. If the branch's
  previous PR has already merged, rebuild it from `origin/main` before adding
  new commits (`git checkout -B <branch> origin/main`), rather than stacking
  on stale/merged history.
- Commits must be authored as `Claude <noreply@anthropic.com>` and SSH-signed
  (`git config commit.gpgsign` should already be `true` in this environment).
  If a push is rejected as unsigned, `git commit --amend --no-edit --reset-author`
  fixes the tip commit.

## Gotchas

None recorded yet — nothing is built. Add operational gotchas here as they
bite (deploy pipeline, persistence, AI integration quirks, etc.).
