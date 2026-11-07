# Run artifacts

Every file lives in the chosen durable run dir outside the checkout. Write JSON with a serializer, never by hand-concatenating strings.

## inventory.tsv

Written by `skills/browser-qa/scripts/changes.sh`. Columns `id kind file line symbol`, one row per changed hunk (`C001`...) plus one per deleted file (`D-<name>`). Rows of kind `test` and `docs` count as traced without a claim. Never edit it; regenerate it when the head moves.

## traces/<slice>.md

One per tracer. Free prose, but every claim carries `file:line`. Ends with the tracer's return block (see `skills/browser-qa/reference/tracer-brief.md`).

## features.json

Written by the orchestrator after merging traces.

```json
{
  "head": "<full sha>",
  "features": [
    {
      "id": "F01",
      "name": "Instructor edits a lecture and saves it",
      "entry": "/course-management/:courseId/lectures/:lectureId/edit",
      "roles": ["instructor", "editor"],
      "changes": ["C004", "C011"],
      "chain": [
        "lecture-update.component.html:88 (click) save()",
        "lecture.service.ts:41 PUT api/lecture/lectures",
        "LectureResource.java:142 updateLecture",
        "LectureService.java:77 save"
      ],
      "endpoints": [
        { "call": "PUT /api/lecture/lectures", "role": "@EnforceAtLeastEditorInCourse", "changed": "response" }
      ],
      "branches": [
        { "id": "B1", "where": "LectureService.java:80", "what": "end date before start date rejected" }
      ],
      "states": ["lecture with units", "lecture without units"],
      "writes": true,
      "no_permission_case": null
    }
  ],
  "excused": [
    { "change": "C017", "reason": "log message text only, no behavior a user can reach" }
  ]
}
```

- `endpoints` lists every REST endpoint on the chain. `changed` is `request`, `response`, `auth`, `behavior` or `none` (on the chain, not changed). Any value but `none` forces an `api` case.
- `roles` are Artemis course roles: `student`, `tutor`, `editor`, `instructor`, `admin`, plus `anonymous` for public pages.
- `branches` are reject, guard, and alternate paths inside changed code that a user input can reach. A branch no input can reach goes in the trace, not here.
- `writes: true` forces a `revisit` case. `no_permission_case` holds a reason string when a permission case is meaningless (a public page), else `null`.
- `excused` is for changes with no user-reachable effect. "Covered by unit tests" is not a reason; "constant renamed, no runtime effect" is.

## matrix.md

Write one row per affected feature, screen and role, with links to its changed paths and planned case IDs. Record status (`planned`, `pass`, `fail`, `blocked` or `not tested`) and evidence or the reason it is missing for each case. Include changed paths excluded from user testing with a checkable reason. Note documented workflows and roles, any gaps in the trace, and the selected E2E specs or why selection was unavailable. Automated spec selection and code coverage do not prove a manual case passed. Update the matrix after each case and verify the evidence before reporting a pass.

## plan.json

```json
{
  "head": "<full sha>",
  "target": { "server": 8080, "client": 9000, "seed": "e2e" },
  "cases": [
    {
      "id": "F01-01",
      "feature": "F01",
      "role": "instructor",
      "kind": "happy",
      "account": "artemis_test_user_16",
      "precondition": "seed course <courseId> has a lecture with two units",
      "steps": ["open /course-management/<courseId>/lectures", "click the lecture title", "click Edit", "..."],
      "expect": {
        "ui": "toast 'Lecture updated', new title in the list after reload",
        "requests": ["PUT /api/lecture/lectures 200"],
        "persisted": "reload shows the new title"
      },
      "branches": [],
      "batch": "instructor-lecture"
    }
  ]
}
```

- `kind` is one of `happy`, `negative`, `boundary`, `permission`, `revisit`, `regression` (a neighbouring feature that shares a changed callee), `state` (a lifecycle variant), `api` (a direct REST call, see below).
- An `api` case names `"endpoint": "<METHOD path>"` matching an entry in the feature's `endpoints`, and its `steps` are `skills/browser-qa/scripts/api.sh` calls. `expect.requests` gives the status and the fields the response must hold or must not hold.
- When a negative or permission case expects an error or warning toast, specify its text and severity in `expect.ui`. Other error or warning toasts are unexpected.
- `steps` are user actions only. A step that calls the API directly needs `"lever": "<why no user path reaches this state>"` on the case.
- `batch` groups cases that one driver runs in one browser session: same role, same start screen, no case undoing another's precondition.

## results/<case>.json

Written by the driver, one per case.

```json
{
  "case": "F01-01",
  "verdict": "pass",
  "observed": "toast 'Lecture updated'; title shown after reload",
  "requests": [{ "method": "PUT", "url": "/api/lecture/lectures", "status": 200, "note": "body has title, startDate" }],
  "toasts": [],
  "console_errors": [],
  "evidence": ["shots/F01-01-1-before.png", "shots/F01-01-2-after-reload.png", "net/F01-01.txt"],
  "video": "video/instructor-lecture.webm",
  "video_at": "01:42",
  "deviations": [],
  "blocker": null
}
```

- `verdict` is `pass` (every `expect` line observed), `fail` (an `expect` line contradicted, an unexpected error or warning toast appeared, or an app console error appeared), or `blocked` (a precondition could not be reached; `blocker` names it and the step).
- `toasts` hold every toast seen during the case, verbatim, with severity. `console_errors` hold app-raised errors only, with the first stack frame.
- `video` and `video_at` are required for every browser case that is not `blocked`. An `api` case has neither; its evidence includes the `api/<name>.txt` files and any nonempty response bodies used for assertions.
- `deviations` record every step the driver could not run as written and what it did instead.

## report.md

The orchestrator's deliverable. See the Report step in `skills/browser-qa/SKILL.md`.
