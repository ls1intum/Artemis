# Jackson Cyclic-Reference Race — DTO Refactor Plan

> **For the next agent picking this up:** This plan is self-contained. Do not assume context from the conversation that produced it. Read the **Background** section first; it explains the precise Jackson bug you are working around. Use `superpowers:executing-plans` or `superpowers:subagent-driven-development` to drive task-by-task. Checkbox (`- [ ]`) syntax tracks progress.

**Goal:** Eliminate the Jackson `"No _valueDeserializer assigned"` flake that intermittently fails the server test suite (and is a latent production bug on a small set of `@RequestBody` endpoints) by replacing every REST request body and response that contains a cyclically related JPA entity with a purpose-built DTO. The cycles in scope are:

- `User ↔ TutorialGroupRegistration` (via `User.tutorialGroupRegistrations` ↔ `TGR.student`)
- `Post ↔ Reaction` (via `Post.reactions` ↔ `Reaction.post`)
- `Post ↔ AnswerPost` (via `Post.answers` ↔ `AnswerPost.post`)
- `AnswerPost ↔ Reaction` (via `AnswerPost.reactions` ↔ `Reaction.answerPost`)
- `User ↔ SavedPost` (via `User.savedPosts` ↔ `SavedPost.user`)
- `User ↔ Course` (`User.coursesIamInstructorOf` ↔ `Course.instructor`-style refs; transitively involved when User is built)

**Non-goals:**
- Liquibase changes. The cycles are *only* a Jackson serialization concern; the DB graph stays as-is.
- Frontend type generation. The frontend already has hand-written TypeScript models. After this PR, regenerate via `pnpm run openapi:generate` if the affected endpoints have `@Operation`/springdoc coverage; otherwise update the matching TS interfaces by hand. **Track frontend updates as a follow-up PR if scope explodes** — see task L (last).
- Removing the `JacksonDeserializerInitializationConfig` priming. Keep it as a defence-in-depth even after the DTOs land; it is now correct (see `src/test/java/de/tum/cit/aet/artemis/shared/JacksonDeserializerInitializationConfig.java`) and surfaces any future regression at INFO-level startup logs.

**Tech Stack:** Java 25, Spring Boot 3.5, Gradle 9.3, Spotless, Checkstyle, JUnit 6, pnpm 11 / Node 24, Angular 21, TypeScript, Vitest, ESLint, Jackson 2.21.

**Branch suggestion:** `feature/jackson-cyclic-reference-dto-refactor` off `develop` (after PR #12712 lands).

---

## Background — the bug you are fixing

Jackson's `DeserializerCache._createAndCache2` (jackson-databind 2.21.2) builds a `BeanDeserializer` in three phases under a single `ReentrantLock`:

1. Construct the deserializer (every bean property's `valueDeserializer` is the placeholder `FailingDeserializer`).
2. Put it in `_incompleteDeserializers` so cyclic references can find the in-progress instance.
3. Call `resolve(ctxt)` — walks the property table and replaces every `FailingDeserializer` with the real deserializer. Only after this returns does the entry move to the publicly visible `_cachedDeserializers`.

The lock keeps unrelated callers safe. **The hole is in step 2**: if `resolve` of bean **A** walks into bean **B**, and **B**'s `resolve` walks back into **A** (a cycle), the cyclic lookup returns A's partially-built deserializer from `_incompleteDeserializers`. B's `resolve` reads `prop.getValueDeserializer()` from that partial A, applies `handlePrimaryContextualization` (which calls `BeanDeserializerBase.createContextual` → `withByNameInclusion` because of `@JsonIgnoreProperties` / `@JsonIncludeProperties` on the cross-entity field), and **freezes the resulting contextual variant** with FailingDeserializer placeholders on whichever of A's properties had not yet been resolved at that point.

After A's resolve finishes, A in `_cachedDeserializers` is fully resolved — but the **contextual variant captured in B's property table** is still pointing at the frozen partial state, because `withByNameInclusion` creates a new `BeanPropertyMap` instance (its `SettableBeanProperty` entries are shared by reference, but the `_valueDeserializer` field of each is `protected final` — there is no public surface to re-resolve them).

At test time, when a test deserializes B → its property points at the frozen contextual A → A's `id` property still has `FailingDeserializer` → Jackson throws `"No _valueDeserializer assigned"`. Failing chains we have observed:

```
TutorialGroup["registrations"] → HashSet[0] → TutorialGroupRegistration["student"] → User["id"]
Post["reactions"]              → HashSet[0] → Reaction["user"]                     → User["id"]
```

The same race fires in production whenever a client posts a JSON payload that triggers the first build of a cyclically related deserializer. We have seen it on `PlagiarismPostResource.updatePost(@RequestBody Post)`, `PlagiarismAnswerPostResource.createAnswerPost(@RequestBody AnswerPost)`, and `ExampleSubmissionResource.{create,update}ExampleSubmission(@RequestBody ExampleSubmission)`. The race is **deterministic per JVM**: once a bad contextual variant is in the cache, every subsequent deserialization of that chain fails until the process is restarted.

**Evidence the previous attempts could not close the race:**
- `ObjectMapper.canDeserialize` prime → silently swallowed errors, deprecated in 2.18.
- `findRootValueDeserializer` prime → loud failures, but cannot prevent the contextual variant capture because the variant is created during the same single-threaded prime, mid-resolve.
- Reflective surgery on `BeanDeserializerBase._beanProperties` to re-resolve contextual variants → `SettableBeanProperty._valueDeserializer` is `protected final`. Requires `Unsafe` / `setAccessible` over final, which we will not ship in test code, let alone production.
- Re-priming with `flushCachedDeserializers` between rounds → the same cycle re-creates the same partial-state capture.

**The only fix that works at the framework level** is to make sure the JSON shape that crosses the network does not contain the cyclic relations. That is what this PR does. Entity → JSON happens via response serialization (and via `@RequestBody` deserialization on the inbound side); replacing both with DTOs that contain only the fields the client needs eliminates the cyclic JSON graph and therefore eliminates the chance of the race ever firing.

---

## High-level architecture of the fix

For each cyclic entity we introduce **two** flavours of DTO:

- A **Response DTO** (`*ResponseDTO`) — what the server returns. Contains only the fields actually needed by callers. References to other entities are either inlined as small projection DTOs (e.g. `UserSummaryDTO` with `{id, login, name}`) or replaced with a bare id (`Long`). No back-references. Never has the cyclic field. Built by a `Mapper` (static utility, no Spring bean — keep it simple).
- A **Request DTO** (`*RequestDTO`) — what the server accepts as `@RequestBody`. Fields are exactly what the endpoint needs to mutate. No nested User. No nested cyclic collections. The endpoint reads the path-variable id and pulls the entity from the repository, then applies the DTO's fields.

Both DTOs are **Java `record` types** (Artemis convention; see `CreatePostDTO`, `ExamUpdateDTO`, `TutorialGroupDetailDataDTO` for precedent). Records are `final`, immutable, and equality-stable — Jackson handles them with `@JsonCreator`-equivalent canonical-constructor binding out of the box, so no annotation noise.

Mappers live next to the DTOs (`*.dto.*Mapper` package, static methods, package-private constructor). Pattern:

```java
public final class TutorialGroupMapper {
    private TutorialGroupMapper() {}

    public static TutorialGroupResponseDTO toResponse(TutorialGroup tg) {
        return new TutorialGroupResponseDTO(
            tg.getId(),
            tg.getTitle(),
            // ...
            tg.getRegistrations().stream()
                .map(TutorialGroupMapper::toRegistrationResponse)
                .collect(Collectors.toSet())
        );
    }

    public static TutorialGroupRegistrationResponseDTO toRegistrationResponse(TutorialGroupRegistration r) {
        return new TutorialGroupRegistrationResponseDTO(
            r.getId(),
            UserMapper.toSummary(r.getStudent()),    // breaks the cycle
            r.getType()
        );
    }
}
```

**Where the DTOs live:**
- Communication-related DTOs: `src/main/java/de/tum/cit/aet/artemis/communication/dto/`
- Tutorial-group DTOs: `src/main/java/de/tum/cit/aet/artemis/tutorialgroup/dto/`
- Assessment / ExampleSubmission DTOs: `src/main/java/de/tum/cit/aet/artemis/assessment/dto/`
- Plagiarism DTOs: `src/main/java/de/tum/cit/aet/artemis/plagiarism/dto/`
- The shared `UserSummaryDTO` (used by all of them): `src/main/java/de/tum/cit/aet/artemis/account/dto/UserSummaryDTO.java`

**OpenAPI:** Several of these modules are already in the OpenAPI-generated set (`tutorialgroup`, `globalsearch`, `hyperion`, `calendar`). For those modules, add `@Operation` / `@Schema` annotations so the spec at `/v3/api-docs` regenerates correctly. For non-OpenAPI modules (`communication`, `plagiarism`, `assessment`), no spec work needed.

---

## DTO inventory

Field signatures below are the **canonical record components** in order. `Long? = nullable`, `non-null` unless marked. Types in `[]` are nested DTOs defined later in this list.

### 1. `account/dto/UserSummaryDTO.java`

Used everywhere `User` appears as a nested reference. Fields chosen to match what `@JsonIncludeProperties` already selectively exposed today (`Posting.author` uses `{id, name, imageUrl, bot}`; `Reaction.user` uses `{id, name}`). The DTO is a superset so every existing call site can use it.

```java
public record UserSummaryDTO(
    Long id,
    String login,           // nullable; we don't always expose login (e.g. in Reaction.user)
    String firstName,       // nullable
    String lastName,        // nullable
    String name,            // computed = firstName + " " + lastName; matches @JsonProperty("name") on User
    String imageUrl,        // nullable
    boolean isBot
) {}
```

**Mapper:** `UserMapper.toSummary(User)`. Returns `null` when input is `null`. Build `name` via `user.getName()` (existing method).

### 2. `communication/dto/PostResponseDTO.java`

Replaces `Post` in responses. Fields gathered from `Posting` + `Post` + actual frontend consumption.

```java
public record PostResponseDTO(
    Long id,
    String content,
    String title,
    DisplayPriority displayPriority,
    Set<String> tags,
    UserSummaryDTO author,
    Instant creationDate,
    Instant updatedDate,
    ConversationRefDTO conversation,      // see #3
    PlagiarismCaseRefDTO plagiarismCase,  // see #4, nullable
    CourseWideContext courseWideContext,  // enum, nullable
    boolean resolved,
    int answerCount,
    int voteCount,
    Set<ReactionResponseDTO> reactions,
    List<AnswerPostResponseDTO> answers   // List; existing serialization is ordered by creationDate
) {}
```

**Mapper:** `PostMapper.toResponse(Post)` in `communication/dto/`.

### 3. `communication/dto/ConversationRefDTO.java`

Bare conversation projection used inside posts. Conversations have their own cycles (channel ↔ course ↔ exercises etc.) — we only need id + display info.

```java
public record ConversationRefDTO(
    Long id,
    String type,           // discriminator: "channel" | "groupChat" | "oneToOneChat"
    String name,           // nullable; channels have a name, 1:1 chats don't
    Long courseId
) {}
```

**Mapper:** `ConversationMapper.toRef(Conversation)`.

### 4. `plagiarism/dto/PlagiarismCaseRefDTO.java`

```java
public record PlagiarismCaseRefDTO(
    Long id,
    Long exerciseId,
    Long studentId
) {}
```

### 5. `communication/dto/AnswerPostResponseDTO.java`

Replaces `AnswerPost` in responses. **No back-reference to the parent post** (that's the cycle break).

```java
public record AnswerPostResponseDTO(
    Long id,
    String content,
    UserSummaryDTO author,
    Instant creationDate,
    Instant updatedDate,
    boolean resolvesPost,
    Set<ReactionResponseDTO> reactions
) {}
```

**Mapper:** `AnswerPostMapper.toResponse(AnswerPost)`.

### 6. `communication/dto/ReactionResponseDTO.java`

```java
public record ReactionResponseDTO(
    Long id,
    UserSummaryDTO user,    // cycle break: was Reaction.user with @JsonIncludeProperties({id,name})
    String emojiId,
    Instant creationDate
    // post / answerPost back-refs intentionally omitted
) {}
```

**Mapper:** `ReactionMapper.toResponse(Reaction)`.

> Note: the existing `ReactionDTO` (`communication/dto/ReactionDTO.java`) is for `@RequestBody` on `POST /reactions`. Keep it. Name the response variant explicitly to avoid confusion.

### 7. `tutorialgroup/dto/TutorialGroupResponseDTO.java`

Replaces `TutorialGroup` in responses. The existing `TutorialGroupDetailDataDTO` is a richer variant for the single-group view; this is the lightweight list variant.

```java
public record TutorialGroupResponseDTO(
    Long id,
    String title,
    String additionalInformation,
    Integer capacity,
    Boolean isOnline,
    String campus,
    String language,
    UserSummaryDTO teachingAssistant,
    CourseRefDTO course,                                       // see #9
    Set<TutorialGroupRegistrationResponseDTO> registrations,  // see #8
    Set<TutorialGroupSessionResponseDTO> tutorialGroupSessions, // optional: only when frontend needs the schedule
    Integer numberOfRegisteredUsers,
    boolean isUserRegistered,
    boolean isUserTutor
    // course / channel back-refs limited to the lightweight DTO
) {}
```

**Mapper:** `TutorialGroupMapper.toResponse(TutorialGroup, currentUser)` (needs the current user to compute `isUserRegistered`/`isUserTutor`).

### 8. `tutorialgroup/dto/TutorialGroupRegistrationResponseDTO.java`

```java
public record TutorialGroupRegistrationResponseDTO(
    Long id,
    UserSummaryDTO student,
    TutorialGroupRegistrationType type
    // tutorialGroup back-ref intentionally omitted — this is the primary cycle break
) {}
```

### 9. `core/dto/CourseRefDTO.java`

Lightweight course projection — `Course` itself has its own cyclic mess (`Course.tutorialGroups`, `Course.users`, etc.). Most consumers only need title + id.

```java
public record CourseRefDTO(
    Long id,
    String title,
    String shortName,
    String color
) {}
```

**Mapper:** `CourseMapper.toRef(Course)`.

### 10. `tutorialgroup/dto/TutorialGroupSessionResponseDTO.java`

```java
public record TutorialGroupSessionResponseDTO(
    Long id,
    Instant start,
    Instant end,
    String location,
    TutorialGroupSessionStatus status,
    String statusExplanation,
    Integer attendanceCount
) {}
```

### 11. `assessment/dto/ExampleSubmissionResponseDTO.java`

Replaces `ExampleSubmission` in responses **and** as `@RequestBody`. ExampleSubmission's cyclic refs come via `submission.participation.user` etc.

```java
public record ExampleSubmissionResponseDTO(
    Long id,
    String exampleSubmission,    // the JSON-blob the existing entity stores
    boolean usedForTutorial,
    String assessmentExplanation,
    SubmissionSummaryDTO submission,  // see #12, nullable
    Long exerciseId
) {}
```

### 12. `assessment/dto/SubmissionSummaryDTO.java`

Bare submission projection. Submissions have their own User chain via `participation.student`, hence the cycle.

```java
public record SubmissionSummaryDTO(
    Long id,
    String type,                  // "modeling" | "text" | "programming" | "file-upload" | "quiz"
    Instant submissionDate,
    boolean submitted,
    String submissionText,        // nullable; only populated for text/file-upload variants
    String model,                 // nullable; only modeling
    Long participationId,
    UserSummaryDTO participant    // student or team-leader
) {}
```

### 13. `assessment/dto/ExampleSubmissionCreateRequestDTO.java`

Replaces `@RequestBody ExampleSubmission` for `createExampleSubmission` and `updateExampleSubmission`. The endpoint pulls the live entity from the DB by id and applies these fields.

```java
public record ExampleSubmissionCreateRequestDTO(
    Long id,                          // null on create, set on update
    String exampleSubmission,
    boolean usedForTutorial,
    String assessmentExplanation,
    Long submissionId                 // nullable; references existing Submission
) {}
```

### 14. `plagiarism/dto/PlagiarismPostUpdateRequestDTO.java`

Replaces `@RequestBody Post` on `PlagiarismPostResource.updatePost`. Editing a post only mutates content/title/tags/displayPriority.

```java
public record PlagiarismPostUpdateRequestDTO(
    String content,
    String title,
    Set<String> tags,
    DisplayPriority displayPriority   // nullable
) {}
```

### 15. `plagiarism/dto/PlagiarismAnswerPostCreateRequestDTO.java`

Replaces `@RequestBody AnswerPost` on `PlagiarismAnswerPostResource.createAnswerPost`.

```java
public record PlagiarismAnswerPostCreateRequestDTO(
    Long postId,                  // parent post; required
    String content,
    boolean resolvesPost
) {}
```

### 16. `plagiarism/dto/PlagiarismAnswerPostUpdateRequestDTO.java`

```java
public record PlagiarismAnswerPostUpdateRequestDTO(
    String content,
    boolean resolvesPost
) {}
```

**Total: 16 new DTOs.** (My earlier estimate of 13 missed `ConversationRefDTO`, `PlagiarismCaseRefDTO`, and `SubmissionSummaryDTO`. Those are *implied* — once you start mapping `PostResponseDTO`, you have to handle the conversation field; once you start mapping `ExampleSubmissionResponseDTO`, you have to handle the submission field.)

---

## REST endpoint surface — every endpoint to update

### Production code (responses)

For each row: change return type + use the relevant mapper to convert before returning.

| File | Method | Old signature | New signature |
|---|---|---|---|
| `tutorialgroup/web/TutorialGroupResource.java:163` | `getTutorialGroupsForCourse` | `ResponseEntity<List<TutorialGroup>>` | `ResponseEntity<List<TutorialGroupResponseDTO>>` |
| `communication/web/ConversationMessageResource.java:86` | `createMessage` | `ResponseEntity<Post>` | `ResponseEntity<PostResponseDTO>` |
| `communication/web/ConversationMessageResource.java:115` | `getMessages` | `ResponseEntity<List<Post>>` | `ResponseEntity<List<PostResponseDTO>>` |
| `communication/web/ConversationMessageResource.java:165` | `updateMessage` | `ResponseEntity<Post>` | `ResponseEntity<PostResponseDTO>` |
| `communication/web/ConversationMessageResource.java:205` | `updateDisplayPriority` | `ResponseEntity<Post>` | `ResponseEntity<PostResponseDTO>` |
| `communication/web/ConversationMessageResource.java:221` | `getSourcePostsByIds` | `ResponseEntity<List<Post>>` | `ResponseEntity<List<PostResponseDTO>>` |
| `communication/web/AnswerMessageResource.java:57` | `createAnswerMessage` | `ResponseEntity<AnswerPost>` | `ResponseEntity<AnswerPostResponseDTO>` |
| `communication/web/AnswerMessageResource.java:77` | `updateAnswerMessage` | `ResponseEntity<AnswerPost>` | `ResponseEntity<AnswerPostResponseDTO>` |
| `communication/web/AnswerMessageResource.java:116` | `getSourceAnswerPostsByIds` | `ResponseEntity<List<AnswerPost>>` | `ResponseEntity<List<AnswerPostResponseDTO>>` |
| `plagiarism/web/PlagiarismPostResource.java:92` | `updatePost` | `ResponseEntity<Post>` | `ResponseEntity<PostResponseDTO>` |
| `plagiarism/web/PlagiarismPostResource.java:109` | `getPostsInCourse` | `ResponseEntity<List<Post>>` | `ResponseEntity<List<PostResponseDTO>>` |
| `plagiarism/web/PlagiarismAnswerPostResource.java:52` | `createAnswerPost` | `ResponseEntity<AnswerPost>` | `ResponseEntity<AnswerPostResponseDTO>` |
| `plagiarism/web/PlagiarismAnswerPostResource.java:71` | `updateAnswerPost` | `ResponseEntity<AnswerPost>` | `ResponseEntity<AnswerPostResponseDTO>` |
| `assessment/web/ExampleSubmissionResource.java:87` | `createExampleSubmission` | `ResponseEntity<ExampleSubmission>` | `ResponseEntity<ExampleSubmissionResponseDTO>` |
| `assessment/web/ExampleSubmissionResource.java:106` | `updateExampleSubmission` | `ResponseEntity<ExampleSubmission>` | `ResponseEntity<ExampleSubmissionResponseDTO>` |
| `assessment/web/ExampleSubmissionResource.java:143` | `handleExampleSubmission` (private helper) | `ResponseEntity<ExampleSubmission>` | `ResponseEntity<ExampleSubmissionResponseDTO>` |
| `assessment/web/ExampleSubmissionResource.java:160` | `getExampleSubmission` | `ResponseEntity<ExampleSubmission>` | `ResponseEntity<ExampleSubmissionResponseDTO>` |
| `assessment/web/ExampleSubmissionResource.java:202` | `importExampleSubmission` | `ResponseEntity<ExampleSubmission>` | `ResponseEntity<ExampleSubmissionResponseDTO>` |

### Production code (`@RequestBody`)

| File | Method | Old signature | New signature |
|---|---|---|---|
| `plagiarism/web/PlagiarismPostResource.java:92` | `updatePost` | `@RequestBody Post post` | `@RequestBody PlagiarismPostUpdateRequestDTO postUpdate` |
| `plagiarism/web/PlagiarismAnswerPostResource.java:52` | `createAnswerPost` | `@RequestBody AnswerPost answerPost` | `@RequestBody PlagiarismAnswerPostCreateRequestDTO request` |
| `plagiarism/web/PlagiarismAnswerPostResource.java:71` | `updateAnswerPost` | `@RequestBody AnswerPost answerPost` | `@RequestBody PlagiarismAnswerPostUpdateRequestDTO request` |
| `assessment/web/ExampleSubmissionResource.java:87` | `createExampleSubmission` | `@RequestBody ExampleSubmission` | `@RequestBody ExampleSubmissionCreateRequestDTO` |
| `assessment/web/ExampleSubmissionResource.java:106` | `updateExampleSubmission` | `@RequestBody ExampleSubmission` | `@RequestBody ExampleSubmissionCreateRequestDTO` |
| `assessment/web/TutorParticipationResource.java:109` | `assessExampleSubmissionForTutorParticipation` | `@RequestBody ExampleSubmission` | `@RequestBody ExampleSubmissionCreateRequestDTO` |

### WebSocket payloads

The websocket layer broadcasts `Post` / `AnswerPost` over STOMP topics (search for `messagingTemplate.convertAndSend` in `communication/`). The serialization there uses the same `ObjectMapper`, so the same race fires. **After** updating REST responses to DTOs, audit every `messagingTemplate.convertAndSend(...)` call in:

- `communication/service/ConversationMessagingService.java`
- `communication/service/MessagingService.java`
- `communication/service/notifications/*`

and replace any `Post` / `AnswerPost` / `Reaction` payload with the matching `*ResponseDTO`. This is the second-largest hotspot after REST.

---

## Test surface

`grep -rEn "(getList|getSet|get|postWithResponseBody|putWithResponseBody).*\b(Post|AnswerPost|Reaction|TutorialGroup|ExampleSubmission)\.class" src/test/java/` yields **~128 call sites** today. They fall into these files:

### Files that deserialize via `RequestUtilService` (need updates to `*ResponseDTO`)

- `src/test/java/de/tum/cit/aet/artemis/tutorialgroup/TutorialGroupIntegrationTest.java` (4 sites; `getList(... TutorialGroup.class)`)
- `src/test/java/de/tum/cit/aet/artemis/plagiarism/PlagiarismAnswerPostIntegrationTest.java` (~12 sites; `Post.class`, `AnswerPost.class`)
- `src/test/java/de/tum/cit/aet/artemis/plagiarism/PlagiarismPostIntegrationTest.java` (~15 sites)
- `src/test/java/de/tum/cit/aet/artemis/communication/MessageIntegrationTest.java` (~30 sites; `Post.class`, `List<Post>`)
- `src/test/java/de/tum/cit/aet/artemis/communication/AnswerMessageIntegrationTest.java` (~20 sites)
- `src/test/java/de/tum/cit/aet/artemis/communication/ConversationMessageIntegrationTest.java` (varies)
- `src/test/java/de/tum/cit/aet/artemis/assessment/ExampleSubmissionIntegrationTest.java`
- `src/test/java/de/tum/cit/aet/artemis/assessment/AssessmentComplaintIntegrationTest.java`
- `src/test/java/de/tum/cit/aet/artemis/tutorialgroup/AbstractTutorialGroupIntegrationTest.java` (base test helpers)

**Pattern of change:** every

```java
Post created = request.postWithResponseBody(url, postDto, Post.class, HttpStatus.CREATED);
```

becomes

```java
PostResponseDTO created = request.postWithResponseBody(url, postDto, PostResponseDTO.class, HttpStatus.CREATED);
```

and the downstream assertions (`created.getId()`, `created.getContent()`, etc.) become record-accessor calls (`created.id()`, `created.content()`). Where the test previously asserted on `created.getAuthor()` (a `User`), it now asserts on `created.author()` (a `UserSummaryDTO`).

### Files that build payloads to send (need updates to `*RequestDTO`)

The same files above also construct outgoing payloads. Today they often build an entity (`new Post()`, `setContent(...)`, `setAuthor(user)`, etc.) and send it via `postWithResponseBody`. After this PR, build the matching request DTO instead. **Util factories** that need updating:

- `src/test/java/de/tum/cit/aet/artemis/communication/util/PostFactory.java`
- `src/test/java/de/tum/cit/aet/artemis/communication/util/ConversationFactory.java`
- `src/test/java/de/tum/cit/aet/artemis/plagiarism/util/PlagiarismCaseUtilService.java`
- `src/test/java/de/tum/cit/aet/artemis/assessment/util/ExampleSubmissionFactory.java`
- `src/test/java/de/tum/cit/aet/artemis/tutorialgroup/util/TutorialGroupFactory.java`

Add a sibling DTO-builder method to each (e.g. `PostFactory.createPostRequestDTO(...)`) so tests opt in incrementally.

### Frontend (deferred — see task L)

Files that consume the changed response shape, by directory:

- `src/main/webapp/app/communication/shared/service/*.ts` (posts/answer-posts/reactions services)
- `src/main/webapp/app/tutorialgroup/shared/service/*.ts`
- `src/main/webapp/app/assessment/shared/service/example-submission.service.ts`

The frontend's response types are hand-written TS interfaces in `src/main/webapp/app/{communication,tutorialgroup,assessment}/shared/entities/*.ts`. Update each interface so its shape matches the new DTO (rename `author: User` → `author: UserSummary`, drop the back-reference fields). Run `pnpm run lint && pnpm run typecheck && pnpm run vitest:run`.

---

## Implementation order

The tasks below are intentionally small and atomic so each can land as its own commit (and ideally as its own PR if scope explodes — there is no functional coupling between, say, `TutorialGroup` and `ExampleSubmission`, so they can ship independently). Each task is verifiable end-to-end with `./gradlew compileJava compileTestJava -x webapp` and `./gradlew test --tests <newly-changed-IntegrationTest>` before moving to the next.

### Task A — Foundation: shared `UserSummaryDTO` + mapper

Goal: land the shared DTO so subsequent tasks just import it.

- [ ] Create `src/main/java/de/tum/cit/aet/artemis/account/dto/UserSummaryDTO.java` (spec #1 above).
- [ ] Create `src/main/java/de/tum/cit/aet/artemis/account/dto/UserMapper.java` with a `toSummary(User)` static. Treat null → null.
- [ ] Write `src/test/java/de/tum/cit/aet/artemis/account/dto/UserMapperTest.java`: cover `name` composition, null tolerance, `isBot` mapping.
- [ ] `./gradlew compileJava compileTestJava -x webapp && ./gradlew spotlessCheck checkstyleMain modernizer -x webapp`. All green.

### Task B — `CourseRefDTO` + `CourseMapper.toRef`

- [ ] `src/main/java/de/tum/cit/aet/artemis/core/dto/CourseRefDTO.java` (spec #9).
- [ ] `src/main/java/de/tum/cit/aet/artemis/core/dto/CourseMapper.java` with `toRef(Course)`.
- [ ] Unit test (`src/test/java/de/tum/cit/aet/artemis/core/dto/CourseMapperTest.java`).
- [ ] Build green.

### Task C — Tutorial group response chain

Closes the `TutorialGroup → registrations → TGR → student → User` cycle. The two server-test failures we observed are in this chain (`TutorialGroupIntegrationTest > getTutorialGroupsForCourse_asInstructorOfCourse_shouldShowPrivateInformation`).

- [ ] Create `tutorialgroup/dto/TutorialGroupResponseDTO.java`, `TutorialGroupRegistrationResponseDTO.java`, `TutorialGroupSessionResponseDTO.java` (specs #7, #8, #10).
- [ ] Create `tutorialgroup/dto/TutorialGroupMapper.java` with `toResponse(TutorialGroup, User currentUser)`, `toRegistrationResponse(...)`, `toSessionResponse(...)`. Compute `numberOfRegisteredUsers` from `tg.getRegistrations().size()`. `isUserRegistered` = any registration whose `student.getId().equals(currentUser.getId())`. `isUserTutor` = `tg.getTeachingAssistant() != null && tg.getTeachingAssistant().getId().equals(currentUser.getId())`.
- [ ] Update `tutorialgroup/web/TutorialGroupResource.java:163` (`getTutorialGroupsForCourse`) to return `List<TutorialGroupResponseDTO>`. Inside the method, after fetching the list, call `tutorialGroups.stream().map(tg -> TutorialGroupMapper.toResponse(tg, currentUser)).toList()`. Inject `UserRepository` if needed for `currentUser`.
- [ ] Update `src/test/java/de/tum/cit/aet/artemis/tutorialgroup/TutorialGroupIntegrationTest.java` — every `request.getList(.../tutorial-groups, ..., TutorialGroup.class)` → `TutorialGroupResponseDTO.class`. Update downstream assertions.
- [ ] Update `src/test/java/de/tum/cit/aet/artemis/tutorialgroup/AbstractTutorialGroupIntegrationTest.java` helpers in the same way.
- [ ] Verify: `./gradlew test --tests 'TutorialGroupIntegrationTest' -x webapp` passes. **This is the test that fails today.**

### Task D — Communication response chain (Post + AnswerPost + Reaction)

Closes the `Post → reactions → Reaction → user → User` cycle and the matching AnswerPost variant.

- [ ] Create `communication/dto/ReactionResponseDTO.java` (#6) + `ReactionMapper.toResponse(Reaction)`.
- [ ] Create `communication/dto/AnswerPostResponseDTO.java` (#5) + `AnswerPostMapper.toResponse(AnswerPost)`.
- [ ] Create `communication/dto/ConversationRefDTO.java` (#3) + `ConversationMapper.toRef(Conversation)`. Use `instanceof` (or pattern matching) over `Channel`/`OneToOneChat`/`GroupChat` for the `type` discriminator.
- [ ] Create `plagiarism/dto/PlagiarismCaseRefDTO.java` (#4).
- [ ] Create `communication/dto/PostResponseDTO.java` (#2) + `PostMapper.toResponse(Post)`.
- [ ] Update `communication/web/ConversationMessageResource.java`: 5 methods to return `PostResponseDTO`. Map at the boundary; the service layer keeps returning entities.
- [ ] Update `communication/web/AnswerMessageResource.java`: 3 methods to return `AnswerPostResponseDTO`.
- [ ] Update `plagiarism/web/PlagiarismPostResource.java`: 2 methods (`getPostsInCourse`, `updatePost`).
- [ ] Update `plagiarism/web/PlagiarismAnswerPostResource.java`: 2 methods (`createAnswerPost`, `updateAnswerPost`).
- [ ] Update tests: every `*MessageIntegrationTest`, `PlagiarismPostIntegrationTest`, `PlagiarismAnswerPostIntegrationTest`. Pattern: `Post.class` → `PostResponseDTO.class`, `AnswerPost.class` → `AnswerPostResponseDTO.class`, accessor changes (`.getContent()` → `.content()`).
- [ ] Verify: `./gradlew test --tests 'MessageIntegrationTest' --tests 'AnswerMessageIntegrationTest' --tests 'PlagiarismPostIntegrationTest' --tests 'PlagiarismAnswerPostIntegrationTest' -x webapp` passes.

### Task E — WebSocket payloads

- [ ] Grep for `messagingTemplate.convertAndSend` in `communication/service/` (~10–20 sites).
- [ ] For each call sending a `Post` / `AnswerPost` / `Reaction`, wrap with the matching `*Mapper.toResponse`.
- [ ] No automated test surface for this directly — verify via the `WebsocketServiceTest` suite and the `ConversationMessagingServiceTest` if it exists. Also re-run the e2e suite (`./run-e2e-tests-local-fast.sh --filter "Message|Reaction"`).

### Task F — Plagiarism request bodies

The smallest scope on the request side.

- [ ] Create `plagiarism/dto/PlagiarismPostUpdateRequestDTO.java` (#14), `PlagiarismAnswerPostCreateRequestDTO.java` (#15), `PlagiarismAnswerPostUpdateRequestDTO.java` (#16).
- [ ] Update `PlagiarismPostResource.updatePost`: load the existing `Post` by `postId`, apply the request DTO's fields (content/title/tags/displayPriority), save, return `PostMapper.toResponse(saved)`.
- [ ] Update `PlagiarismAnswerPostResource.createAnswerPost`: build new `AnswerPost`, set parent post from `request.postId()`, content from `request.content()`, save.
- [ ] Update `PlagiarismAnswerPostResource.updateAnswerPost`: load by id, apply, save.
- [ ] Update `PlagiarismPostIntegrationTest` + `PlagiarismAnswerPostIntegrationTest` test payloads to build the request DTOs instead of full entities. Look for `postWithResponseBody(..., postToSave, ...)` / `putWithResponseBody(...)` and swap the second argument.
- [ ] Verify: `./gradlew test --tests 'PlagiarismPostIntegrationTest' --tests 'PlagiarismAnswerPostIntegrationTest' -x webapp` passes.

### Task G — ExampleSubmission request + response

The widest surface for the assessment module.

- [ ] Create `assessment/dto/SubmissionSummaryDTO.java` (#12) + a `SubmissionMapper.toSummary(Submission)` that switches over the submission subtype (`ModelingSubmission`, `TextSubmission`, `ProgrammingSubmission`, `FileUploadSubmission`, `QuizSubmission`) and populates the right optional fields.
- [ ] Create `assessment/dto/ExampleSubmissionResponseDTO.java` (#11) + `ExampleSubmissionMapper.toResponse(ExampleSubmission)`.
- [ ] Create `assessment/dto/ExampleSubmissionCreateRequestDTO.java` (#13).
- [ ] Update `ExampleSubmissionResource.createExampleSubmission`, `updateExampleSubmission`, `getExampleSubmission`, `importExampleSubmission` — all 4 returns become `ExampleSubmissionResponseDTO`. The two write endpoints take `ExampleSubmissionCreateRequestDTO`. The internal `handleExampleSubmission` helper signature follows.
- [ ] Update `TutorParticipationResource.assessExampleSubmissionForTutorParticipation`: request body switches to `ExampleSubmissionCreateRequestDTO`. Internal handler resolves the entity from id.
- [ ] Update `src/test/java/de/tum/cit/aet/artemis/assessment/ExampleSubmissionIntegrationTest.java` and any other test that touches this resource (search: `grep -rln ExampleSubmission src/test/java`).
- [ ] Verify the assessment-module tests.

### Task H — Audit residual @RequestBody/`ResponseEntity` of entities

Sanity sweep — there may be missed sites in modules not on the original list.

- [ ] `grep -rEn "@RequestBody\s+\b(Post|AnswerPost|Reaction|TutorialGroup|TutorialGroupRegistration|ExampleSubmission|User|Course)\b" src/main/java/`
- [ ] `grep -rEn "ResponseEntity<\b(Post|AnswerPost|Reaction|TutorialGroup|TutorialGroupRegistration|ExampleSubmission)\b" src/main/java/`
- [ ] Expected residual is exactly the production endpoints we know are fine (those that already use DTOs). Anything else → add to this plan and back to the relevant Task above.

### Task I — Re-enable `JacksonDeserializerInitializationConfig` self-test

After Tasks A–H, the cyclic chain should no longer be reachable through any JSON the test ObjectMapper deserializes. Now turn the synthetic prime-time deserialize back on as a defence-in-depth.

- [ ] Re-add the `exerciseFailureChains(TypeFactory)` method to `src/test/java/de/tum/cit/aet/artemis/shared/JacksonDeserializerInitializationConfig.java` (the version that was reverted at the end of PR #12712 — find it in the git history of that file). It does `objectMapper.readValue(json, type)` against the two synthetic JSON shapes that triggered the race.
- [ ] Add new chains specific to your fix to the synthetic JSON, e.g. `[{"id":1,"reactions":[{"id":1,"user":{"id":1}}]}]` for `List<PostResponseDTO>` (must succeed because the new DTOs do not have the cycle).
- [ ] Verify: `./gradlew test --tests 'TutorialGroupIntegrationTest' --tests 'MessageIntegrationTest' --tests 'OrganizationIntegrationTest' -x webapp`. All three test classes that flaked in PR #12712 must now pass deterministically.

### Task J — Full server test run

- [ ] `./gradlew test -x webapp`. Must finish with 0 failures. Allow 30–45 min.
- [ ] If a test that was *not* in the directly-touched list fails, investigate before merging — it is almost certainly an entity-shape assumption we missed.

### Task K — Multi-node e2e

- [ ] `./run-e2e-tests-local-multinode-fast.sh`. Allow 12–15 min. The pre-existing flakes (`Create an exercise team` ~65% failure rate, `git submission through HTTPS` ~3% failure rate, `Instructor can see complaint and reject it` — both observed in PR #12712 e2e) are acceptable here as long as they retry-pass or fail at the same rate as develop.

### Task L — Frontend types (separate follow-up PR if scope explodes)

The client today has hand-written TS interfaces for `Post`, `AnswerPost`, `Reaction`, `TutorialGroup`, `TutorialGroupRegistration`, `User`. After the server returns DTOs, those interfaces are wrong (extra fields no longer come back; `author: User` is now actually `author: UserSummary`).

- [ ] Audit `src/main/webapp/app/{communication,tutorialgroup,assessment,plagiarism}/shared/entities/`. For every interface that mirrors an entity in scope, either (a) update the interface to match the response DTO, or (b) introduce a new interface alongside the existing one and migrate consumers.
- [ ] Run `pnpm run lint && pnpm run typecheck && pnpm run vitest:run`. Fix every type error.
- [ ] If this balloons (more than ~30 files), split into its own PR titled `Communication / TutorialGroup / Assessment: align frontend types with new server DTOs`.

---

## Verification per task

After each task above, run **all** of:

```bash
./gradlew compileJava compileTestJava -x webapp
./gradlew spotlessCheck checkstyleMain modernizer -x webapp
./gradlew test --tests '<TaskSpecificIntegrationTest>' -x webapp
```

Commit only when all three are green. Use the existing commit conventions (`Communication: ...`, `Tutorial groups: ...`, `Assessment: ...`).

---

## Risks and rollout

- **Breaking change for API consumers.** Any external integration that reads `Post`/`AnswerPost`/`TutorialGroup` JSON shape directly will break. Artemis's only documented public API consumer is its own frontend, so coordinate with the frontend changes in Task L. Mobile apps (`@ls1intum/mobile-apps-maintainers`) also consume these endpoints — ping them before merging.
- **The `Posting.author` field today has `@JsonIgnoreProperties(value = "author")`.** After this PR, the entity is no longer the serialization shape, so that annotation becomes a no-op for the response path. Leave it in place anyway — it still affects the `@RequestBody` deserialization for endpoints we did not migrate (none should remain, but defence-in-depth).
- **Hibernate proxy issues.** When mapping `tg.getTeachingAssistant()` etc., the entity may be a lazy proxy. The mapper accesses fields via getter (`getId()`, `getLogin()`), which triggers initialisation within the same Hibernate session as the REST handler — this is the same pattern existing DTOs (`TutorialGroupDetailDataDTO`) use, so it is known-safe. Do not introduce mapper calls from outside an open transaction (e.g. in `@Async` methods) without verifying the session is still attached.
- **Mappers calling other mappers** (`PostMapper` → `UserMapper.toSummary`) create a small static call graph. Keep mappers pure and side-effect free; no Spring injection, no logging in the hot path.
- **Equality / hashCode in test assertions.** Several existing tests do `assertThat(returnedPost).usingRecursiveComparison().ignoringFields(...).isEqualTo(expectedPost)`. After the DTO change, the recursive comparison fields shift. Update `ignoringFields(...)` patterns to match the DTO's field names (record components, not JavaBean getters).
- **STOMP topic payload version skew.** A user with an old web client still connected when the server upgrades will get a payload shaped differently than the client expects. The Artemis deploy process restarts all nodes simultaneously (rolling), and a websocket reconnect happens automatically on disconnect — this is not a new risk, but be aware that during the 30s window of a deploy, clients may see a single failed websocket frame.

---

## Estimated effort

- Tasks A–B (foundation): ~½ day.
- Task C (TutorialGroup): ~½ day. **High value — closes the known flake.**
- Task D (Communication): ~1–1.5 days. Largest surface.
- Task E (WebSocket): ~½ day.
- Task F (Plagiarism request bodies): ~½ day.
- Task G (ExampleSubmission): ~1 day.
- Task H (audit): ~½ day.
- Task I (re-enable self-test): ~¼ day.
- Tasks J–K (full test runs): runtime only; ~1 hour wall-clock.
- Task L (frontend, if in scope): ~1 day.

**Server-side total: ~5–6 days of focused work.** Suitable for one PR if the maintainer is willing to review a ~2–3k line diff, otherwise split at the module boundary (one PR per Task C/D/G).

---

## Out of scope but worth flagging

- The `User.coursesIamInstructorOf` ↔ `Course.instructor` cycle is not directly exercised by any failing test today, because no endpoint returns the full `Course` graph in JSON. It could fire if a future endpoint does. The conservative move is to also introduce `CourseResponseDTO` and a corresponding mapper — but only if a new endpoint needs it.
- `SavedPost` and `Authority` form their own cycles with `User`. They are not serialized today (every SavedPost endpoint already uses `PostingDTO`), so they are safe to leave.
- The frontend has its own analogous race surface in Angular's `HttpClient` when parsing recursive response types, but Angular's JSON.parse is cycle-naïve (it just builds the tree as-is) — no equivalent Jackson issue.

---

## File-level diff size estimate

- New files: ~16 DTOs + ~6 mappers + ~6 mapper tests ≈ 28 new files, ~1500 LOC.
- Modified production files: ~6 resources + ~3 messaging services ≈ 9 files, ~300 LOC of changes.
- Modified test files: ~8 integration tests + ~5 test util/factory classes ≈ 13 files, ~600 LOC of changes.
- Total: **~50 files, ~2400 LOC**, of which most is mechanical DTO + mapper boilerplate. Reviewable as one PR by a tolerant reviewer; comfortable as two PRs split at the communication / tutorialgroup boundary.
