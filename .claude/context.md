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

**Status: early.** The home page (`/`, light grey like the calendar) lists today's classes and a checkable to-do list (`done` flag) from the calendar's events, with a "Calendar page" button (top right) linking to `/calendar.html`, a month-grid calendar where you
can add/delete events (title + optional time) on a selected day. Events live
in the browser's `localStorage` (key `classly.events`) — no backend storage
yet. A camera button (bottom-left) takes a photo/upload of a schedule or
agenda, sends it to `POST /api/import-photo` (Gemini vision → JSON items),
and shows a review/edit sheet before adding to the calendar; weekly classes
(tagged `kind: 'class'`) are expanded 16 weeks forward. A small "Hello Gemini" button in the footer remains as a deploy check.

**Rotating day cycle (e.g. 6-day schedule):** the 🔁 button on the calendar
page takes two photos — a year calendar (`POST /api/import-cycle-calendar`:
cycle day per date, plus PD days/holidays) and a day schedule
(`POST /api/import-cycle-schedule`: classes per cycle day). Both live in
`localStorage` (`classly.cycleDays`, `classly.cycleClasses`) and `cycle.js`
expands them into calendar events with `source: 'cycle'` (classes, plus
`kind: 'info'` labels like "Day 3"/"PD Day" that the home page shows next to
the date). Re-importing regenerates all `source: 'cycle'` events.
The home page's "Schedule page" button opens `/schedule.html`: a "Take photo of
schedule" button (camera or photo library) that imports the day schedule and
then shows it as a Day 1…N digital schedule. `cyclelib.js` (`CycleStore`) holds
the shared storage/event-generation used by both pages.

## Architecture at a glance

Same stack as `foodie` (reused deliberately):

- **Backend**: Kotlin + Ktor (Netty), single service in `backend/`, package
  `com.classly`. Static front end (`src/main/resources/static/`: plain
  HTML + JS, no framework or templating yet).
- **AI**: Gemini REST API via `GeminiClient` (`RestGeminiClient`), model
  `gemini-3.6-flash`. Expected to also do schedule/agenda photo extraction
  (user reviews/edits the result before saving) and the chat planner (tool
  calling that writes calendar entries).
- **Storage/auth**: not yet decided (foodie uses Firestore + magic-link).
- **Deploy**: Cloud Build (`cloudbuild.yaml`) → Artifact Registry → Cloud
  Run, triggered by commits to this repo.

Capabilities still to design: calendar data model (classes, assignments,
tests, projects, study/homework blocks, reminders, notes), per-user storage.

## Configuration reference

- GCP project ID `foodie-503510` (shared with foodie), region
  `northamerica-northeast1`, Artifact Registry repo `cloud-run-source-deploy`.
- Cloud Run service `classly`; image `classly-backend:${SHORT_SHA}`.
  Live URL: https://classly-124314901354.northamerica-northeast1.run.app
- `GEMINI_API_KEY` is set on the Cloud Run service (Variables & Secrets),
  not in the repo. Locally: `GEMINI_API_KEY=... ./gradlew run` from `backend/`.
- Endpoints: `POST /api/hello-gemini` (no input); `POST /api/import-photo`
  (`{image: base64, mimeType, today}` → `{events: [...]}`).

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

- Client platform beyond the web page (PWA install, Android TWA like foodie,
  native).
- Storage and auth model, and whether accounts are per-student only.
- How reminders are delivered (push, email, in-app only).
- Changing school terms (the rotating-day cycle itself is handled, see above).

## Maintenance

Update this file when architecture, a major feature, or a decision changes.
