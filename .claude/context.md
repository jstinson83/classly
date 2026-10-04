# Classly — Project Context

Stable overview of the project. Update this when architecture, infra, or
major conventions change — not for day-to-day task status (see `current.md`
for that). Keep this high-level: architecture, decisions, and concrete
config facts. Operational gotchas — what breaks, how it bit us before, how
to debug it — belong in `CLAUDE.md` instead; don't duplicate its detail
here.

## What this is

A school organization app so everything school-related lives in one place.
Photograph your class schedule and each day is filled in; photograph your
agenda/planner and the assignments land on the calendar; add reminders and
notes to any assignment; chat with an AI to plan when to do homework or
study for a test/project and have the plan written to the calendar
automatically.

Solo project, same maintainer as `foodie` (sibling repo whose workflow
conventions this repo mirrors — see `CLAUDE.md`).

**Status: planning only. No code, stack, or infra exists yet.**

## Architecture at a glance

Not decided yet. See "Open decisions" below. Capabilities any stack must
cover:

- Image → structured data (schedule photo → classes/times per weekday;
  agenda photo → assignments with due dates). Vision-capable LLM is the
  expected approach, with user review/edit of the extracted result before
  it is saved.
- Calendar data model: classes (recurring), assignments, tests, projects,
  study/homework blocks with start/end times, reminders, notes.
- Conversational planner: LLM with tool/function calling that creates and
  edits calendar entries directly.
- Per-user storage and auth.

## Pages (original product spec)

- **Home**: today's to-do list, today's classes, buttons to the other pages.
- **Calendar**: what's due/tested at school, what homework to do, what to
  study. Each day shows a time slot for each item. A "+" in the corner adds
  items manually. Also where photo-imported assignments appear.
- **Schedule**: digital version of the class schedule (populated from a
  schedule photo or manual entry).
- **Notes**: extra notes per day, free-form and editable; notes can also be
  attached to individual assignments.
- **AI chat**: tell it about a test/project due date; it helps plan work
  sessions and automatically puts them on the calendar.

Cross-cutting: reminders can be added to items.

## Decisions / things already considered

- Photo capture is the headline input (schedule + agenda); manual entry via
  the Calendar "+" is the fallback and the correction path.
- The AI chat writes to the calendar itself, not just suggests.

## Open decisions

- Platform (PWA vs native Android/iOS vs cross-platform) and backend stack.
  Foodie is a Kotlin/Ktor PWA + Android TWA on Cloud Run/Firestore; reuse is
  possible but not assumed.
- LLM/vision provider for photo extraction and the chat planner.
- Auth model and whether accounts are per-student only.
- How reminders are delivered (push, email, in-app only).
- Handling rotating/A-B-day schedules and changing school terms.

## Maintenance

Update this file when architecture, a major feature, or a decision changes.
