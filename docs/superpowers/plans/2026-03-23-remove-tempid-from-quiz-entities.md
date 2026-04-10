# Remove tempID from Quiz Entities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `@Transient tempID` from quiz domain entities, keeping it only in the DTO transport layer where it belongs. Fix the quiz creation/edit rendering bug.

**Architecture:** `tempID` is a creation-time concept for linking mappings to items before they have database IDs. Currently it leaks into JPA entities via `TempIdObject`. The refactoring moves mapping resolution from entity-level tempID lookup to index-based resolution at the DTO/service boundary. After persistence, all references use real database `id` values. The client edit view uses `id`-based matching for items loaded from the server.

**Tech Stack:** Java 25 (Spring Boot), TypeScript/Angular 21, Hibernate, Vitest, JUnit 6

---

## Context: The Current Bug

After creating a quiz via POST (which returns `QuizExerciseWithStatisticsDTO`), the edit and preview pages don't render quiz content. Root cause: the POST response DTO contains DnD/SA items and mappings as separate object copies. The client's `onSaveSuccess` uses this response directly, but the edit components can't match mapping items to array items because they're different JS object instances with no `tempID` (it's `@Transient` and not in response DTOs). The old entity-based response used `@JsonIdentityInfo` to maintain object identity, which DTOs don't have.

## File Structure

### Files to Modify (Server)
- `src/main/java/de/tum/cit/aet/artemis/quiz/domain/TempIdObject.java` — DELETE entirely
- `src/main/java/de/tum/cit/aet/artemis/quiz/domain/DragItem.java` — Remove `extends TempIdObject`
- `src/main/java/de/tum/cit/aet/artemis/quiz/domain/DropLocation.java` — Remove `extends TempIdObject`
- `src/main/java/de/tum/cit/aet/artemis/quiz/domain/ShortAnswerSolution.java` — Remove `extends TempIdObject`
- `src/main/java/de/tum/cit/aet/artemis/quiz/domain/ShortAnswerSpot.java` — Remove `extends TempIdObject`
- `src/main/java/de/tum/cit/aet/artemis/quiz/service/QuizExerciseService.java` — Refactor `resolveQuizQuestionMappings` to use index-based resolution
- `src/main/java/de/tum/cit/aet/artemis/quiz/dto/question/create/*CreateDTO.java` — Keep tempID in DTOs, resolve mappings at DTO level
- `src/main/java/de/tum/cit/aet/artemis/quiz/dto/question/fromEditor/*FromEditorDTO.java` — Replace tempID with index-based or id-based matching

### Files to Modify (Client)
- `src/main/webapp/app/quiz/manage/update/quiz-exercise-update.component.ts` — Fix `onSaveSuccess` to properly handle DTO response
- `src/main/webapp/app/quiz/shared/entities/drop-location.model.ts` — Remove `BaseEntityWithTempId`, use `BaseEntity` with optional `tempID` only for creation
- `src/main/webapp/app/quiz/shared/entities/quiz-question.model.ts` — Update `resetQuizQuestionForImport` to use index-based approach
- `src/main/webapp/app/quiz/shared/service/drag-and-drop-question-util.service.ts` — Update `isSameEntityWithTempId` to prefer `id`
- `src/main/webapp/app/quiz/shared/service/short-answer-question-util.service.ts` — Update comparison methods

### Test Files
- `src/test/java/de/tum/cit/aet/artemis/quiz/util/QuizExerciseFactory.java` — Remove tempID generation
- `src/test/java/de/tum/cit/aet/artemis/quiz/QuizExerciseIntegrationTest.java` — Update tests
- Client spec files for affected components

---

## Phase 1: Fix the Immediate Bug (Quiz Creation Rendering)

### Task 1: Fix onSaveSuccess to reconcile DTO response with component state

The POST response is a DTO where mapping items are separate JS object copies from the question's item arrays. The edit view needs them to be the SAME references. After receiving the response, reconcile mappings by replacing nested items with references from the arrays (matched by `id`).

**Files:**
- Modify: `src/main/webapp/app/quiz/manage/update/quiz-exercise-update.component.ts`

- [ ] **Step 1: Add a reconcileMappingReferences helper method**

After the POST/PUT response is received and before rendering, iterate through DnD/SA questions and replace the nested mapping item copies with the actual array references:

```typescript
private reconcileMappingReferences(quizExercise: QuizExercise): void {
    for (const question of quizExercise.quizQuestions ?? []) {
        if (question.type === QuizQuestionType.DRAG_AND_DROP) {
            const dnd = question as DragAndDropQuestion;
            const dragItemById = new Map(dnd.dragItems?.map(di => [di.id, di]) ?? []);
            const dropLocationById = new Map(dnd.dropLocations?.map(dl => [dl.id, dl]) ?? []);
            for (const mapping of dnd.correctMappings ?? []) {
                if (mapping.dragItem?.id) mapping.dragItem = dragItemById.get(mapping.dragItem.id) ?? mapping.dragItem;
                if (mapping.dropLocation?.id) mapping.dropLocation = dropLocationById.get(mapping.dropLocation.id) ?? mapping.dropLocation;
            }
        } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
            const sa = question as ShortAnswerQuestion;
            const solutionById = new Map(sa.solutions?.map(s => [s.id, s]) ?? []);
            const spotById = new Map(sa.spots?.map(s => [s.id, s]) ?? []);
            for (const mapping of sa.correctMappings ?? []) {
                if (mapping.solution?.id) mapping.solution = solutionById.get(mapping.solution.id) ?? mapping.solution;
                if (mapping.spot?.id) mapping.spot = spotById.get(mapping.spot.id) ?? mapping.spot;
            }
        }
    }
}
```

- [ ] **Step 2: Call reconcileMappingReferences in onSaveSuccess**

In `onSaveSuccess`, after setting `this.quizExercise = quizExercise`, call `this.reconcileMappingReferences(this.quizExercise)` before `prepareEntity`.

- [ ] **Step 3: Run client tests**

Run: `npm run vitest:run -- src/main/webapp/app/quiz`
Expected: All 790 tests pass

- [ ] **Step 4: Manual test — create quiz with MC + DnD questions, verify edit view renders**

- [ ] **Step 5: Commit**

```
Quiz creation: reconcile DTO mapping references for edit view rendering
```

---

## Phase 2: Remove tempID from Server Domain Entities

### Task 2: Refactor resolveQuizQuestionMappings to use DTO-level index resolution

Instead of setting `tempID` on entities and resolving via `getTempID()`, resolve mappings at the DTO level before converting to domain objects. The Create DTOs already have `tempID` fields — build index maps from the DTO lists and resolve mapping references using array indices.

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/service/QuizExerciseService.java`
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/dto/exercise/QuizExerciseCreateDTO.java`
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/dto/question/create/DragAndDropQuestionCreateDTO.java`
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/dto/question/create/ShortAnswerQuestionCreateDTO.java`

- [ ] **Step 1: Move mapping resolution into DragAndDropQuestionCreateDTO.toDomainObject()**

Instead of creating stub DragItem/DropLocation with only tempID in mappings, resolve the mappings using the tempID-keyed maps from the same DTO's drag items and drop locations:

```java
public DragAndDropQuestion toDomainObject() {
    DragAndDropQuestion question = new DragAndDropQuestion();
    // ... set base fields ...

    List<DropLocation> dropLocs = dropLocations.stream().map(DropLocationCreateDTO::toDomainObject).toList();
    List<DragItem> items = dragItems.stream().map(DragItemCreateDTO::toDomainObject).toList();
    question.setDropLocations(dropLocs);
    question.setDragItems(items);

    // Resolve mappings using tempID maps from the DTO lists
    Map<Long, DragItem> tempIdToDragItem = new HashMap<>();
    for (int i = 0; i < dragItems.size(); i++) {
        tempIdToDragItem.put(dragItems.get(i).tempID(), items.get(i));
    }
    Map<Long, DropLocation> tempIdToDropLoc = new HashMap<>();
    for (int i = 0; i < dropLocations.size(); i++) {
        tempIdToDropLoc.put(dropLocations.get(i).tempID(), dropLocs.get(i));
    }

    List<DragAndDropMapping> mappings = correctMappings.stream().map(m -> {
        DragAndDropMapping mapping = new DragAndDropMapping();
        mapping.setDragItem(tempIdToDragItem.get(m.dragItemTempId()));
        mapping.setDropLocation(tempIdToDropLoc.get(m.dropLocationTempId()));
        if (mapping.getDragItem() == null || mapping.getDropLocation() == null) {
            throw new BadRequestAlertException("Invalid mapping tempID", "quizExercise", "invalidMappings");
        }
        return mapping;
    }).toList();
    question.setCorrectMappings(mappings);

    return question;
}
```

- [ ] **Step 2: Apply same pattern to ShortAnswerQuestionCreateDTO.toDomainObject()**

- [ ] **Step 3: Remove resolveQuizQuestionMappings from QuizExerciseService** (or simplify it — it's no longer needed for creation since DTOs handle it)

- [ ] **Step 4: Remove tempID from DragItemCreateDTO/DropLocationCreateDTO toDomainObject()** — no longer need to set tempID on the entity

- [ ] **Step 5: Run server tests**

Run: `./gradlew test --tests "de.tum.cit.aet.artemis.quiz.*" -x webapp`
Expected: All pass

- [ ] **Step 6: Commit**

```
Quiz creation: resolve mappings at DTO level instead of entity tempID
```

### Task 3: Remove TempIdObject and tempID from domain entities

**Files:**
- Delete: `src/main/java/de/tum/cit/aet/artemis/quiz/domain/TempIdObject.java`
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/domain/DragItem.java`
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/domain/DropLocation.java`
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/domain/ShortAnswerSolution.java`
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/domain/ShortAnswerSpot.java`
- Modify: `src/main/java/de/tum/cit/aet/artemis/quiz/util/QuizExerciseFactory.java`

- [ ] **Step 1: Update entity classes to extend QuizQuestionComponent directly** (remove `extends TempIdObject`)
- [ ] **Step 2: Delete TempIdObject.java**
- [ ] **Step 3: Update QuizExerciseFactory to remove tempID generation**
- [ ] **Step 4: Update FromEditorDTOs to use `id` instead of `tempID` for existing items** (effectiveId returns id)
- [ ] **Step 5: Update ReEvaluate DTOs** — use array index or id-based matching instead of tempID
- [ ] **Step 6: Run full server test suite**

Run: `./gradlew test -x webapp`
Expected: All 11,537 tests pass

- [ ] **Step 7: Commit**

```
Quiz entities: remove TempIdObject and @Transient tempID from domain layer
```

---

## Phase 3: Clean Up Client-Side tempID Usage

### Task 4: Remove BaseEntityWithTempId and tempID from client models

**Files:**
- Modify: `src/main/webapp/app/quiz/shared/entities/drop-location.model.ts`
- Modify: `src/main/webapp/app/quiz/shared/entities/drag-item.model.ts`
- Modify: `src/main/webapp/app/quiz/shared/entities/short-answer-solution.model.ts`
- Modify: `src/main/webapp/app/quiz/shared/entities/short-answer-spot.model.ts`
- Modify: `src/main/webapp/app/quiz/shared/service/drag-and-drop-question-util.service.ts`
- Modify: `src/main/webapp/app/quiz/shared/service/short-answer-question-util.service.ts`
- Modify: `src/main/webapp/app/quiz/shared/entities/quiz-question.model.ts` (`resetQuizQuestionForImport`)
- Modify: `src/main/webapp/app/quiz/shared/entities/quiz-exercise-creation/quiz-question-creation-dto.model.ts`
- Delete: `src/main/webapp/app/quiz/manage/util/temp-id.ts`

- [ ] **Step 1: Keep `tempID` as an optional field on models** (needed only for creation DTOs sent TO server), but remove auto-generation in constructor
- [ ] **Step 2: Update `isSameEntityWithTempId` to `isSameEntity`** — match by `id` only (tempID no longer in server responses)
- [ ] **Step 3: Update `isSameSolution` and `isSameSpot`** — match by `id` only
- [ ] **Step 4: Update `resetQuizQuestionForImport`** — generate tempIDs only for the creation DTO conversion (not on the model objects themselves); use index-based mapping for import
- [ ] **Step 5: Update creation DTO conversion** — generate tempIDs at conversion time, not on model construction
- [ ] **Step 6: Update edit components** — remove `delete spot.tempID` cleanups (no longer needed)
- [ ] **Step 7: Run all client tests**

Run: `npm run vitest:run -- src/main/webapp/app/quiz`
Expected: All 790 tests pass

- [ ] **Step 8: Commit**

```
Quiz client: remove tempID from models, use id-based matching
```

---

## Phase 4: Final Verification

### Task 5: Full test suite and manual verification

- [ ] **Step 1: Run full server test suite**: `./gradlew test -x webapp`
- [ ] **Step 2: Run full client test suite**: `npm run vitest:run`
- [ ] **Step 3: Run E2E tests**: `./run-e2e-tests-local-fast.sh --filter "Quiz"`
- [ ] **Step 4: Manual test matrix:**
  - Create MC quiz → verify edit renders → save → verify re-edit
  - Create DnD quiz → verify mappings render → save → verify re-edit
  - Create SA quiz → verify mappings render → save → verify re-edit
  - Import quiz from another course → verify all data
  - Re-evaluate quiz with new solutions → verify
  - Preview and solution view → verify
- [ ] **Step 5: Commit and push**

```
Quiz: verify tempID removal works end-to-end
```
