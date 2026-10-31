---
name: artemis-exercises
description: Create and edit draft text and modeling exercises in an Artemis course. Use when the instructor wants a new text or modeling exercise written, or an unpublished one revised. Programming exercises have their own skill.
---

# Text and modeling exercises

Read `artemis-access` first. This skill needs a token with the scope `exercises:draft` and the editor role in the
course. Exercises of exams are out of reach for a token.

## Drafts only

A token can create an exercise only with a `releaseDate` in the future, and can change only an exercise that students
cannot see yet. The change must keep `releaseDate` in the future. If you get a 403 on a change, the exercise is
already published: tell the instructor to change it in the web interface. Never clear or backdate `releaseDate`.

## Find exercises

```bash
H=(-H "Authorization: Bearer $ARTEMIS_TOKEN" -H "Content-Type: application/json")
curl -sS "${H[@]}" "$ARTEMIS_URL/api/text/courses/$COURSE_ID/text-exercises"          # summaries
curl -sS "${H[@]}" "$ARTEMIS_URL/api/modeling/courses/$COURSE_ID/modeling-exercises"
curl -sS "${H[@]}" "$ARTEMIS_URL/api/text/text-exercises/$EXERCISE_ID"                # all fields of one
```

## Create a text exercise

```bash
curl -sS -X POST "${H[@]}" "$ARTEMIS_URL/api/text/text-exercises" -d '{
  "title": "Essay on recursion",
  "problemStatement": "Explain when recursion is a better fit than a loop. Give two examples.",
  "maxPoints": 10,
  "includedInOverallScore": "INCLUDED_COMPLETELY",
  "courseId": 123,
  "mode": "INDIVIDUAL",
  "releaseDate": "2027-01-10T08:00:00Z",
  "dueDate": "2027-01-24T22:00:00Z"
}'
```

The dates are examples. Set `releaseDate` to a time later than now, and `dueDate` after it, or the request is refused
with 400. `startDate` is optional; if supplied, it must also be in the future, or the request returns 400.
The response contains the new `id`. A modeling exercise is created the same way at `POST /api/modeling/modeling-exercises`
and also needs a `diagramType`, which cannot be changed later. See `/agent/openapi.yaml` for the values.

`includedInOverallScore` is `INCLUDED_COMPLETELY`, `INCLUDED_AS_BONUS` or `NOT_INCLUDED`. Dates must be in order:
`releaseDate`, then `dueDate`, then `assessmentDueDate`.

## Change a draft

An update replaces the whole exercise, so never send only the changed field.

1. Read the exercise with `GET`.
2. Change what the instructor asked for in the JSON you got back.
3. Send all of it with `PUT $ARTEMIS_URL/api/text/text-exercises` (or `/api/modeling/modeling-exercises`).

## Writing the exercise

Write the problem statement in Markdown. State what the student must deliver, how it is graded and how many points
each part earns. Show the instructor the text and the points before you create the exercise.
