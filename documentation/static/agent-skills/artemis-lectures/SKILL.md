---
name: artemis-lectures
description: Create lectures in Artemis and add draft units with slides or a video, then check their processing status. Use when the instructor wants lecture slides uploaded to a course.
---

# Lectures and slides

Read `artemis-access` first. This skill needs a token with the scope `lectures:write` and the editor role in the course.

## Drafts only

A lecture has no release date of its own. What students see are its units, so every unit you add needs a
`releaseDate` in the future, and so does its attachment. The instructor publishes in the web interface.

## Create a lecture

```bash
H=(-H "Authorization: Bearer $ARTEMIS_TOKEN" -H "Content-Type: application/json")
curl -sS -X POST "${H[@]}" "$ARTEMIS_URL/api/lecture/lectures" -d '{"title":"Lecture 7: Recursion","course":{"id":123}}'
```

`startDate` and `endDate` are optional and must be in order. The response contains the lecture `id`.

## Add slides or a video

A unit is sent as `multipart/form-data`. For slides there are three parts: `attachmentVideoUnit`, `attachment` and
`file`.

```bash
curl -sS -X POST -H "Authorization: Bearer $ARTEMIS_TOKEN" \
  -F 'attachmentVideoUnit={"name":"Slides","releaseDate":"2027-01-10T08:00:00Z"};type=application/json' \
  -F 'attachment={"name":"Lecture 7 slides","attachmentType":"FILE","releaseDate":"2027-01-10T08:00:00Z"};type=application/json' \
  -F 'file=@lecture7.pdf;type=application/pdf' \
  "$ARTEMIS_URL/api/lecture/lectures/$LECTURE_ID/attachment-video-units"
```

For a video, send only the first part, with `"videoSource": "<address of the video>"` added.

The unit is returned at once. Splitting the PDF into slides and indexing it for Iris continue in the background.

## Check the processing

```bash
curl -sS "${H[@]}" "$ARTEMIS_URL/api/lecture/lectures/$LECTURE_ID/lecture-units/statuses"
```

Each entry has a `processingPhase`: `IDLE`, `TRANSCRIBING`, `INGESTING`, `DONE`, `FAILED` or `SKIPPED`. Poll every few
seconds, not faster, until it is `DONE` or `SKIPPED`. The list is empty when processing is not enabled on the instance.
On `FAILED`, tell the instructor; a token cannot retry processing.
