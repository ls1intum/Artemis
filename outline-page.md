# Exercise Variants Discussion

# Exercise Variants — Design Alternatives

Practice mode is done. Next step: adaptive exercise variation.

The original proposal extends the exam `ExerciseGroup` to courses. You asked for deeper competency integration and suggested showing competency relationships at the exercise level. Below are six options, then our take.


---

## Current State

 ![](https://outline.aet.cit.tum.de/api/files.get?sig=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJ1cGxvYWRzL2FhMmVmNjRjLTE3OTQtNDdhOC1hN2I1LTRkNjRhMDg2YWFhZS85YTZkMTlhOC02NTA4LTQ2YzgtYmNkOC0wNTdjN2MyYzRmMDAvQmlsZHNjaGlybWZvdG8gMjAyNi0wNS0xMSB1bSAxNS4wMi41Ni5wbmciLCJ0eXBlIjoiYXR0YWNobWVudCIsImlhdCI6MTc4MDY2NTMxNywiZXhwIjoxNzgxMjcwMTE3fQ.0CFPTGc7cyD_bfRr81uPiUXgb1m3p8FwUNIMAtnlBPQ " =895x678")

* No variant concept exists.
* `ExerciseGroup` is exam-only.
* Exercises link to competencies one by one.


---

## Approach 1 — Extend `ExerciseGroup` to Courses

Add `course_id` to `ExerciseGroup`, plus `selectionMode`, `groupMaxPoints`, `sharedDueDate`, `practiceOnly`. Competency links stay per-exercise.

 ![](https://outline.aet.cit.tum.de/api/files.get?sig=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJ1cGxvYWRzL2FhMmVmNjRjLTE3OTQtNDdhOC1hN2I1LTRkNjRhMDg2YWFhZS9lZTM2ODg2Ny1iNjljLTQyOTQtODQzYS1lMjExOTk3OWE5MWMvQmlsZHNjaGlybWZvdG8gMjAyNi0wNS0xMSB1bSAxNS4wMy4wNy5wbmciLCJ0eXBlIjoiYXR0YWNobWVudCIsImlhdCI6MTc4MDY2NTMxNywiZXhwIjoxNzgxMjcwMTE3fQ.0HoR12xi8Itd5pBejhpU_GpgTVwAF0zO16z8DgFqamA " =895x215")

* Pro: no new entities, lowest effort.
* Con: one entity doing two unrelated jobs (exam XOR course).
* Con: competency integration stays UI-only.



---

## Approach 2 — Self-Referencing Exercise

Add `parentExerciseId` and `variantSequence` to `Exercise`. Group settings sit on the parent.

 ![](https://outline.aet.cit.tum.de/api/files.get?sig=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJ1cGxvYWRzL2FhMmVmNjRjLTE3OTQtNDdhOC1hN2I1LTRkNjRhMDg2YWFhZS84YjM3OGM4Yy1lNmJiLTRlNGYtYWU5Yi1hOGJlNGJlYTBiNmUvQmlsZHNjaGlybWZvdG8gMjAyNi0wNS0xMSB1bSAxNS4wMy4yNi5wbmciLCJ0eXBlIjoiYXR0YWNobWVudCIsImlhdCI6MTc4MDY2NTMxNywiZXhwIjoxNzgxMjcwMTE3fQ.Y-DwVfkJnSFHoHM7EszctgGUTPUsxyhcTPwT8F2YzaM " =895x272")

* Pro: tiny schema change, fits AI generation ("clone and link to parent").
* Con: group settings on the parent feel wrong.
* Con: querying "all variants of X" is awkward.


---

## Approach 3 — Dedicated `ExerciseVariantGroup`

New entity in the `exercise` module. Exam's `ExerciseGroup` stays untouched.

 ![](https://outline.aet.cit.tum.de/api/files.get?sig=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJ1cGxvYWRzL2FhMmVmNjRjLTE3OTQtNDdhOC1hN2I1LTRkNjRhMDg2YWFhZS9iODA3NmVhZS1lODIwLTQyOTctYTQxNS02NzhhOTRiMTIwNGUvQmlsZHNjaGlybWZvdG8gMjAyNi0wNS0xMSB1bSAxNS4wMy4zOC5wbmciLCJ0eXBlIjoiYXR0YWNobWVudCIsImlhdCI6MTc4MDY2NTMxNywiZXhwIjoxNzgxMjcwMTE3fQ.mMtlf99Ls1G8OXedgR7T_gmPxctdf-0CgIQ6NLT2b3o " =895x243")

* Pro: clean separation from exam code.
* Pro: group settings have a proper home.
* Con: competencies still only attach to single exercises, not the group.


---

## Approach 4 — Variants as Relations

New `ExerciseRelation` entity with directed edges. Mirrors `CompetencyRelation`. No container. Variant family = connected exercises in the graph.

 ![](https://outline.aet.cit.tum.de/api/files.get?sig=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJ1cGxvYWRzLzQxNjY0ODBkLTY1YzItNDkxNS1iMTE1LWI0YTMzNTQyNzgwNi9jMTIzMWViYi0wMzI5LTQwYjMtODFmNS1mNTVjOTc1NjcxNGQvaW1hZ2UucG5nIiwidHlwZSI6ImF0dGFjaG1lbnQiLCJpYXQiOjE3ODA2NjUzMTcsImV4cCI6MTc4MTI3MDExN30.gC2MljxAqVG9qscp15U2aPrx2OG15z9aaL-VX2xF74w " =779x446")

* Pro: exercise relationships become real data.
* Pro: matches the existing `CompetencyRelation` pattern.
* Con: no place for group-level settings.
* Con: instructors would need a graph editor, not a list.


* ==nicht schlecht aus Kompatabilitätsgrunden, Konsistenzgründen==
* ==Grouping wo anders gespeichert, ist da oder nciht da. Kann man auch bei Klausuren so machen==
* ==ExerciseGroups aus Klausuren ausbauen (aber nciht im 1. Schritt). Klausuraufgaben haben schon 2 parents, wollen lieber nicht potentiellen 3. parent==
* ==Course als container, Metadata sind one to many gespeichert==
* ==Lieber mit Dopplung CompetencyExerciseLink als mit Exercisevariantgroup==
* ==wenn Group existiert muss man sicherstellen dass exercise gleiche duedates hat==
* ==Wie kann man es UI techisch arbeiten==

  \


**UI**

* bei Lectures haben wir drag and drop.. wie werden die gruppen dargestellt
* muss man nicht alles überarbeiten
* in der group: add new exercise oder link new exercise
* In der gruppe oben anegzeigt
* nicht zu stark von DB design
* Unterschiedliche aufgabentypen in einer gruppe? Kann sein, dass es Sinn ergibt
* What do we need the graph for. Does it make sense?



---

## Approach 5 — Hybrid (VariantGroup + Group Competency Link + Optional Relations)

Approach 3 plus a `CompetencyVariantGroupLink` so a competency can target the whole group.

 ![](https://outline.aet.cit.tum.de/api/files.get?sig=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJ1cGxvYWRzL2FhMmVmNjRjLTE3OTQtNDdhOC1hN2I1LTRkNjRhMDg2YWFhZS80NWFlNTdlMi1lN2IzLTRiOTctYTNiMi1hMTYzZTU0ODUxN2IvQmlsZHNjaGlybWZvdG8gMjAyNi0wNS0xOCB1bSAxNS4wNC41MC5wbmciLCJ0eXBlIjoiYXR0YWNobWVudCIsImlhdCI6MTc4MDY2NTMxNywiZXhwIjoxNzgxMjcwMTE3fQ.pleOaAzPS8gYpECD3ZTPa6JeSDR3r7tuv6IAWoD1px4 " =889x375")

* Pro: group-level competency targeting is real data.
* Pro: adaptive selection has something to work on (`CompetencyProgress` vs `masteryThreshold`).
* Con: two competency link levels — need precedence rules.
* Con: most effort of all options. 


* Competencies should be on a group level.
* Competencies within a group must be consistent
* ==Wenn exerciseVariantGroup löschen, gibt es die aufgaben weiterhin und die relationship gibt es nicht mehr==
* ==Überall wo compenentcy link gibt, muss man schauen ob es ExerciseVariantGroup gibt => lieber vermeiden==
* MJ mag diesen Vorschlag :rocket:


---

## Approach 6 — Typed Collections on `Exercise`

Each `Exercise` gets typed `@ManyToMany` sets to other exercises — one per relation type, one join table each. No new entity class.

Three relations:

| Relation | Direction | Meaning |
|----------|-----------|---------|
| `variantOf` | symmetric | same goal and difficulty, different wording |
| `easierThan` | A → B: A easier than B | pairwise difficulty |
| `prerequisiteFor` | A → B: do A before B | sequencing |

 ![](https://outline.aet.cit.tum.de/api/files.get?sig=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJ1cGxvYWRzL2FhMmVmNjRjLTE3OTQtNDdhOC1hN2I1LTRkNjRhMDg2YWFhZS9jNGZhZjc2Yy1mZjgwLTQ3MGQtYjVlNy0yZjIzZGE4OWMyNjYvQmlsZHNjaGlybWZvdG8gMjAyNi0wNS0xMSB1bSAxNS4wNC4yMy5wbmciLCJ0eXBlIjoiYXR0YWNobWVudCIsImlhdCI6MTc4MDY2NTMxNywiZXhwIjoxNzgxMjcwMTE3fQ.ytDR4vzB3y2kiTO2qEnn06R-awITC7VB_mfhT7QY-Oc " =895x354")

`CompetencyExerciseLink` is dropped from this feature. Sequencing comes from `prerequisiteFor`, not from competency prerequisites. How an exercise links to a competency becomes a separate redesign — which you flagged as wanted anyway.

* Pro: no new entity, no discriminator column.
* Pro: each relation is a plain join-table query.
* Pro: `prerequisiteFor` makes sequencing explicit data.
* Con: adding a relation type needs a migration.
* Con: no place for group-level settings.
* Con: competency attribution is left open.


---

## Comparison

|     | 1   | 2   | 3   | 4   | 5   | 6   |
|-----|:---:|:---:|:---:|:---:|:---:|:---:|
| New entities | 0   | 0   | +1  | +1  | +2 (+1 opt.) | 0   |
| Exam code isolated | no  | yes | yes | yes | yes | yes |
| Group settings | overloaded | on parent | clean | none | clean | none |
| Group-level competency target | no  | no  | no  | no  | yes | deferred |
| Exercise-to-competency link | kept | kept | kept | kept | kept | removed |
| Exercise-level relations | UI only | UI only | UI only | data | data | data |
| Adaptive selection | hard | hard | hard | partial | built in | partial |


---

## Our Take

* Approaches 1 and 2 don't address the competency feedback enough.
* Approach 3 is fine but Approach 5 covers it and adds the group-level competency link.
* Approach 4 is a weaker version of Approach 6.
* Approach 5: safer thesis option. Keeps `CompetencyExerciseLink`, adds group-level link on top.
* Approach 6: more ambitious. Drops `CompetencyExerciseLink` for this feature, replaces it with explicit `prerequisiteFor`. Pushes competency attribution into a follow-up redesign.


Which direction would you like us to take? 4


**TODOs**

wenn eine aufgabe vorgänger von einer aufgabe ist. sind dann alle varianten von der aufgabe in einer Gruppe

mermaid. ufgabengruppen löschen wenn aufgaben nicht mehr existieren

reihenfolge von klassen ändern
