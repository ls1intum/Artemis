---
name: artemis-announcements
description: Draft and post an announcement to an Artemis course. Use when the instructor wants to tell the students of a course something, for example a room change or a deadline. Always confirm the text first.
---

# Announcements

Read `artemis-access` first. This skill needs a token with the scope `announcements:post` and the instructor role in
the course.

## An announcement is live at once

A posted announcement is visible to every student of the course and notifies them immediately. Nothing can be
unposted with a token. So:

1. Write the announcement and show the instructor the exact title and text.
2. Say which course it goes to, by name and id.
3. Post it only after the instructor clearly says yes. A request to "write an announcement" is a request for a draft,
   not for posting.

## Post it

```bash
curl -sS -X POST -H "Authorization: Bearer $ARTEMIS_TOKEN" -H "Content-Type: application/json" \
  "$ARTEMIS_URL/api/communication/courses/$COURSE_ID/announcements" \
  -d '{"title":"Room change","content":"The exam on Friday is now in room 0.1.2 at 10:00."}'
```

- `content` is required and is Markdown. `title` is optional.
- The announcement goes to the course's default announcement channel. If the course has several announcement
  channels, pass `"channelId"`; a channel that is not an announcement channel of the course is rejected with 400.
- A 403 means the instructor is not allowed to announce in this course. Tell them. Do not try another channel or
  endpoint.

Report the id of the post you created.
