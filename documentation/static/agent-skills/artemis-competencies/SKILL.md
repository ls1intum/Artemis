---
name: artemis-competencies
description: Read, draft and maintain the competencies of an Artemis course and the relations between them (the competency graph). Use when the instructor wants competencies created from course material, a graph reviewed or extended, or standardized competencies imported.
---

# Competencies and the competency graph

Read `artemis-access` first. This skill needs a token with the scopes `competencies:read` and, to change anything,
`competencies:write`, and the editor role in the course.

Competencies have no draft state: what you create or change shows in the course for students at once. Propose the list
to the instructor first, and say exactly what you changed afterwards.

## Read the current graph

```bash
C="$ARTEMIS_URL/api/atlas/courses/$COURSE_ID"
H=(-H "Authorization: Bearer $ARTEMIS_TOKEN" -H "Content-Type: application/json")
curl -sS "${H[@]}" "$C/course-competencies"            # competencies and prerequisites, with the caller's progress
curl -sS "${H[@]}" "$C/course-competencies/relations"  # the edges: tailCompetencyId, headCompetencyId, relationType
curl -sS "${H[@]}" "$C/competencies/$ID"               # one competency with its linked exercises and lecture units
```

Always read before you write, so you do not create what exists.

## Propose before you create

When asked to derive competencies from slides, a syllabus or exercises, first show the instructor a table: title,
description, mastery threshold, and the relations you would add. Create nothing until they agree. Prefer a small set
of competencies a student could actually master over a long list of topics.

## Create and change competencies

```bash
# one
curl -sS -X POST "${H[@]}" "$C/competencies" -d '{"title":"Apply recursion","description":"Write recursive functions and argue why they terminate.","masteryThreshold":70}'
# several at once; all or nothing
curl -sS -X POST "${H[@]}" "$C/competencies/bulk" -d '[{"title":"A","masteryThreshold":70},{"title":"B","masteryThreshold":70}]'
# update: send the whole competency including its id
curl -sS -X PUT "${H[@]}" "$C/competencies" -d '{"id":42,"title":"Apply recursion","masteryThreshold":80}'
```

- `title` is required and at most 255 characters. `masteryThreshold` is 1 to 100. `taxonomy` and `softDueDate` are optional.
- Standardized competencies can be added from the catalogue with `POST $C/competencies/import-standardized` and a JSON list of catalogue ids.
- Linking a competency to a lecture unit or an exercise is done on that unit or exercise, with `competencyLinks`, not here.

## Relations

```bash
# the tail ASSUMES, EXTENDS or MATCHES the head
curl -sS -X POST "${H[@]}" "$C/course-competencies/relations" -d '{"tailCompetencyId":43,"headCompetencyId":42,"relationType":"ASSUMES"}'
# change the type of an existing relation
curl -sS -X PATCH "${H[@]}" "$C/course-competencies/relations/$RELATION_ID" -d '{"newRelationType":"EXTENDS"}'
```

A relation that would close a cycle is rejected with 400. To move a relation, create the new one; deleting is not
available to a token, so tell the instructor which relation to remove in the web interface.
