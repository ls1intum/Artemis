---
name: artemis-programming-exercises
description: Create and edit draft programming exercises in Artemis, including their problem statement, dates and test case weights, and edit their code through git. Use when the instructor wants a programming exercise set up or an unpublished one revised.
---

# Programming exercises

Read `artemis-access` first. This skill needs a token with the scope `programming:draft` and the editor role in the
course. Exercises of exams are out of reach for a token.

## Drafts only

A token can create an exercise only with a `releaseDate` in the future. It can change an exercise only while students
cannot see it **and** no student has started it. If you get a 403, the exercise is published or has participations:
tell the instructor to change it in the web interface.

## Read

```bash
H=(-H "Authorization: Bearer $ARTEMIS_TOKEN" -H "Content-Type: application/json")
curl -sS "${H[@]}" "$ARTEMIS_URL/api/programming/courses/$COURSE_ID/programming-exercises"   # summaries
curl -sS "${H[@]}" "$ARTEMIS_URL/api/programming/programming-exercises/$EXERCISE_ID"         # all fields, repository addresses
```

When you create an exercise in the same style as an existing one, read the existing one first and start from its
settings. That is the quickest way to get the many required fields right.

## Create

`POST $ARTEMIS_URL/api/programming/programming-exercises/setup` creates the exercise, its template, solution and test
repositories and its build plan. It takes a while. Required fields:

- `title` (at least 3 characters) and `shortName` (unique in the course), `course` as `{"id": 123}`
- `programmingLanguage`, and `projectType` when the language has project types; Java and Kotlin also need `packageName`
- `buildConfig`, `staticCodeAnalysisEnabled`, `maxPoints`
- at least one of `allowOnlineEditor`, `allowOfflineIde`, `allowOnlineIde`
- `releaseDate` in the future

Validation errors come back as 400 with the name of the field. A 500 means the repositories or the build plan could
not be set up and the exercise may be partly created: look at the course before you try again.

## Change a draft

| Task              | Call                                                                                                                                                                                                                                                                                                                                                               |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Settings          | `PUT /api/programming/programming-exercises` with the whole exercise from `GET`. Omitted flags reset to false. `shortName`, `staticCodeAnalysisEnabled`, the checkout paths and the course cannot change.                                                                                                                                                          |
| Dates             | `PUT /api/programming/programming-exercises/timeline` replaces the whole timeline. Read the exercise first and preserve `releaseDate`, `startDate`, `dueDate`, `assessmentType`, `assessmentDueDate`, `exampleSolutionPublicationDate` and `buildAndTestStudentSubmissionsAfterDueDate` unless changing them. Include `id`; `releaseDate` must stay in the future. |
| Problem statement | `PATCH /api/programming/programming-exercises/{id}/problem-statement` with the Markdown as the plain-text body (`Content-Type: text/plain`).                                                                                                                                                                                                                       |
| Test case weights | `PATCH /api/programming/programming-exercises/{id}/update-test-cases` with a JSON list of `{id, weight, bonusMultiplier, bonusPoints, visibility}`. Test cases appear when the build runs the tests; you cannot create them here.                                                                                                                                  |

The MCP `set_programming_timeline` tool reads the current timeline and merges the supplied fields before sending this replacement.
Omitted fields keep the values from that read; explicit null clears a date. Normal server validation and automatic rebuild scheduling
still apply. Coordinate with the instructor to avoid concurrent edits
between the read and save, which could overwrite a change made in the meantime.

## Edit the code with git

The template, solution and test repositories are ordinary git repositories. Their addresses are in the exercise from
`GET`. Clone them with git and the instructor's VCS access token from the user settings (not the agent token), edit
the files, run the tests locally, commit and push. After a push to the test repository Artemis builds the exercise and
the test cases appear.

Work on the solution first and check that it passes the tests, then derive the template from it by removing what the
students must write. Keep the template compiling.
