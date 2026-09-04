# Removing test-fabricated git repositories from the server tests

Status: implemented
Date: 2026-09-04
Branch: `chore/remove-deprecated-local-repository`

## Problem

`de.tum.cit.aet.artemis.programming.util.LocalRepository` and
`de.tum.cit.aet.artemis.programming.util.LocalRepositoryUriUtil` are `@Deprecated` with the note
"DO NOT USE this class anymore for server tests". They date from the era when creating a
repository in an external version control system was expensive, so tests fabricated a working
copy and a "remote" with JGit.

That premise is gone. With LocalVC and LocalCI as the default modules, a server test can create a
real programming exercise with real template, solution, tests and auxiliary repositories, and can
start the exercise for a student to get a real assignment repository forked from the template.

`LocalRepository` has two modes, and **both** have to go.

**Mode B — a fabricated remote.** `configureRepos(basePath, "someLocalRepo", "someOriginRepo")`
creates a scratch bare repository in a random folder that LocalVC knows nothing about.
`LocalRepositoryUriUtil` then splices a `git` segment into the filesystem path purely so
`LocalVCRepositoryUri` validation accepts it. The participation ends up pointing at a `file://`
path that is not a LocalVC repository. Five call sites remain after the dead code below is
removed.

**Mode A — a hand-built LocalVC repository.** `LocalVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug)`
JGit-inits a bare repository at exactly the path LocalVC would serve. Nothing is mocked, so this
looks acceptable — but it is a *bridge*, not a destination. It reproduces in test code what
`LocalVCService.createRepository` already does in production, and it is what lets the surrounding
fixtures stay wrong. About 31 test classes and ~340 member accesses depend on it.

### The bridge is actively causing work

`ProgrammingExerciseUtilService.addProgrammingExerciseToCourse` and its ~15 siblings persist an
exercise and then hand-attach template and solution participations. The repository URIs they
write point at repositories that **do not exist**. Two hacks exist solely to paper over that:

- `ParticipationUtilService.ensureLocalVcRepositoryExists(...)` — creates the missing bare
  repository behind the caller's back whenever a participation is fabricated.
- `ProgrammingExerciseTestService` pre-creates repositories in `setupRepositoryMocks`, and then
  every exercise-creation test has to call `deleteLocalVcProjectIfPresent(exercise)` again
  (11 call sites) because `ProgrammingExerciseValidationService.checkIfProjectExists` rejects a
  `POST /setup` for a project key whose folder already exists.

Both disappear once the factories create exercises for real.

### Dead code found during the survey

- `ProgrammingUtilTestService` (177 lines). Injected into `ProgrammingExerciseIntegrationTestService`,
  but no method is ever called. All three of its methods use the fabricated-URI trick.
- `ProgrammingExerciseUtilService.createGitRepository()`. Creates a repository, builds a
  `mock(Repository.class)`, stubs two methods on it, and discards it. Its two callers in
  `AthenaRepositoryExportServiceTest` get no effect from it.

### Latent bug the change fixes

`ContinuousIntegrationTestService:48` and `AthenaRepositoryExportServiceTest:57` construct
`new LocalRepository(defaultBranch)` in a **field initialiser**, so the `@Value`-injected
`defaultBranch` is still `null`. JGit falls back to `master` and writes a `refs/heads/null`
symref. The tests pass only because they never depend on the branch.

## Evidence that real exercises are affordable

`LocalVCFetchAndPushIntegrationTest` already creates every exercise through
`POST /api/programming/programming-exercises/setup` and starts participations through
`POST /api/exercise/exercises/{id}/participations`. Measured on this machine:

```
SUCCESS: Executed 30 tests in 2m 5s   (30 passed, 0 failed)
   1.73s  testFetchPush_studentRepository_duringWorkingTime()
   1.57s  testFetchPush_auxiliaryRepository()
   1.54s  testFetchPush_twoStudentsStartExercise()
   1.23s  testFetchPush_testsRepository()
```

Each of those creates a full exercise — template, solution, tests, sometimes an auxiliary
repository, seeded with the language template, with initial submissions and mocked builds — and
several also start one or two student participations, clone over HTTP and push. **1–2 seconds
per test.** Hand-building four repositories with JGit is in the same order of magnitude, so the
change is roughly cost-neutral.

A cheaper path exists for tests that only need the repositories to exist:
`ProgrammingExerciseCreationUpdateService.createProgrammingExercise(exercise, false, /* skipRepositoryAndBuildTrigger */ true)`
runs `createRepositoriesForNewExercise` (real, empty, correct default branch) but skips template
seeding, initial submissions and build plans — so it needs **no** Docker or Jenkins mocking at all.

Every affected test class already runs on a LocalVC-capable profile, so this is reachable
everywhere:

| Base | Profiles | Affected classes |
| --- | --- | --- |
| `AbstractProgrammingIntegrationLocalCILocalVCTest[Base]` | LocalCI + LocalVC | 15 |
| `AbstractSpringIntegrationLocalCILocalVCTest` (incl. Iris bases) | LocalCI + LocalVC | 5 |
| `AbstractSpringIntegrationJenkinsLocalVCTest` (incl. `AbstractAthenaTest`) | Jenkins + **LocalVC** | 6 |

`ProgrammingExerciseCreationUpdateService` and `ParticipationService` are both `PROFILE_CORE`, so
they are available in all of them.

## Design

### Principle

Every repository a server test uses is produced by production code. No test class calls
`Git.init()`.

| Repository | Produced by |
| --- | --- |
| template / solution / tests / auxiliary | `ProgrammingExerciseCreationUpdateService.createProgrammingExercise` — via the service (fast) or `POST /api/programming/programming-exercises/setup` (full) |
| student assignment repository | `ParticipationService.startExercise` — via the service or `POST /api/exercise/exercises/{id}/participations` |
| content inside any repository | clone the real repository, commit, push |

### The mechanism: fixture repositories come from the version control service

The original plan was to route `ProgrammingExerciseUtilService.addProgrammingExerciseToCourse`
and its siblings through `createProgrammingExercise(exercise, false, true)`. Implementation
showed that entry point does considerably more than create repositories: it calls
`userRepository.getUser()` eagerly (so every fixture would need an authenticated context),
creates a communication channel, extracts competency links, runs task extraction, performs
schedule and notification operations, and provisions VCS access tokens asynchronously. Applying
that to the 110 test classes that use these factories would have changed far more than
repositories.

The narrower production entry point is used instead. `LocalVCRepositoryTestService` creates a
repository exactly the way `ProgrammingExerciseRepositoryService#createAndInitializeAuxiliaryRepository`
does — `VersionControlService.createRepository` for the bare repository, `GitService.commitAndPush`
for the initial commit — and nothing else. It is called from the two places that assign
repository URIs to fixtures:

- `ProgrammingExerciseParticipationUtilService`, for the template, solution and tests
  repositories, and
- `ParticipationUtilService`, for a student or team participation's repository.

The principle is unchanged: no test constructs a git repository, and every repository a fixture
points at exists because production code created it. What changed is which production entry
point, chosen to avoid a side-effect blast radius unrelated to the goal.

Tests that need template *content* (not just existing repositories) keep using the full
`POST /setup` path, which they already have to for the CI mocking.

### The handle is a clone, not an initialised repository

`LocalVCTestRepository` replaces `LocalRepository`. The difference that matters is not the name:
its bare repository is always one the version control service created inside the LocalVC folder
structure, and its working copy is an ordinary `git clone` of that repository. Nothing in a test
calls `Git.init()` any more, and the record exposes no way to turn a filesystem path into a
repository URI — that absence is what closes the loophole `LocalRepositoryUriUtil` exploited.

The record is immutable and names what it holds: `bareRepository` (the repository LocalVC serves,
no longer called a remote) and `workingCopy`, both with `Path` rather than `File`.

Working copies are created under `artemis.temp-path`. Previously they landed at
`localVCBasePath/<random6>/<random6>-<name>.git`, which is structurally a valid LocalVC
repository path.

`LocalVCRepositoryTestService.writeFilesAndPush` covers the common "put this file in that
repository" need. It checks the repository out with `GitService`, exactly as the server does, so
no test-owned working copy is involved at all.

### What is deleted

- `LocalRepository`, `LocalRepositoryUriUtil`, `LocalRepositoryTest`
- `LocalVCLocalCITestService.createAndConfigureLocalRepository` and `createRepositoryFolder`
- `RepositoryExportTestUtil`'s fabrication half: `BaseRepositories`, `trackRepository`,
  `cleanupTrackedRepositories`, `resetRepos`, `seedBareRepository`, `seedLocalVcBareFrom`,
  `seedStudentRepositoryForParticipation`, `getOrCreateWorkingCopyForParticipation`,
  `cloneExistingBareRepo`, `createAndWireBaseRepositories(WithHandles)`, `wireRepositoryToExercise`
- `ProgrammingUtilTestService` (dead)
- `ProgrammingExerciseUtilService.createGitRepository` (dead, plus a discarded Mockito mock)
- `ParticipationUtilService.ensureLocalVcRepositoryExists` (both overloads)
- `ProgrammingExerciseTestService.setupRepositoryMocks*`, `configureLocalRepositoryForSlug`,
  `convertToLocalVcUriString`, the `repositoryMetadata` `IdentityHashMap`, the ten public
  `LocalRepository` fields, and the 11 `deleteLocalVcProjectIfPresent(exercise)` calls that only
  undo pre-created repositories

### What is kept

`RepositoryExportTestUtil`'s assertion and synchronisation half: `assertZipContainsFiles`,
`waitForBareRepositoryToContainCommit`, `waitForBareRepositoryReady`, `getLatestCommit`,
`deleteStudentBareRepo`, `deleteLocalVcProjectIfPresent`, `safeDeleteDirectory`,
`deleteDirectoryIfExists` — retargeted from `LocalRepository` to `Git`/`Path`.

### Hard cases

Tests that need a repository in an abnormal state damage a **real** repository after creation
rather than fabricating a broken one:

- `LocalVCIntegrationTest.testFetchPush_repositoryDoesNotExist` — delete the real bare repository,
  or simply point at a slug that was never created; no repository construction needed.
- `LocalCIIntegrationTest:426` ("create a new one with an invalid path") — corrupt the real bare
  repository path.
- `LocalVCIntegrationTest` force-push and foreign-project cases — a second real exercise supplies
  the foreign project key.
- `ProgrammingExerciseGitIntegrationTest` — `LocalRepository.initialize(path, branch, false)`
  builds a *non-bare* scratch repository to exercise `GitService` checkout paths. This is the one
  genuine "unit test that purely works on a repository". It keeps a local JGit repository, created
  inline in that test class with `Git.init()`, with a comment saying why.

## Out of scope

- Production code. This change touches `src/test` only.
- The `mock(Repository.class)` doubles in `PlagiarismDetectionServiceTest`,
  `AbstractArtemisBuildAgentTest` and `ProgrammingExerciseRepositoryServiceTest`, and the
  `GitService` stubs in the Hyperion and Deimos unit tests. None involve `LocalRepository`; they
  are service doubles, not fabricated remotes.

## Implementation stages

Each stage left the test tree compiling and is a separate commit.

**S0 — dead code.** Deleted `ProgrammingUtilTestService` and its field in
`ProgrammingExerciseIntegrationTestService`; deleted `ProgrammingExerciseUtilService.createGitRepository()`
and its two call sites.

**S1 — fixture repositories from the version control service.** Added `LocalVCRepositoryTestService`
and called it from `ProgrammingExerciseParticipationUtilService` (template, solution, tests) and
`ParticipationUtilService` (participation repositories). Rewrote `AthenaRepositoryExportServiceTest`
against real repositories, which uncovered that `ProgrammingExerciseFactory` built the tests
repository slug as `<PROJECTKEY>tests` instead of the `<projectkey>-tests` the server generates.

**S2 — the fabricated-remote call sites**, rewritten against real repositories, after which
`LocalRepositoryUriUtil` and the two `configureRepos(Path, String, String[, boolean])` overloads
had no callers and were deleted.

**S3 — `LocalRepository` deleted.** `LocalVCTestRepository` took its place,
`LocalVCLocalCITestService.createRepositoryWithWorkingCopy` now ensures the repository exists and
clones it, and the ~20 remaining test classes were migrated to the new members.

**S4 — tidy.** Removed the singleton-scoped working copy list, the dead
`RepositoryExportTestUtil.writeAndCommit`, and the redundant per-field cleanup in
`ProgrammingExerciseTestService.tearDown`.

## Verification

- `./gradlew compileTestJava -x webapp` after every stage.
- `./gradlew spotlessApply -x webapp`, then `spotlessCheck checkstyleMain -x webapp`.
- **After S1**, before anything else: run the LocalCI/LocalVC and Jenkins/LocalVC buckets plus the
  programming, exercise and exam modules, because S1 changes the fixtures 110 classes depend on.
- After S3: run every touched test class with `./gradlew test --tests … -x webapp`.
- Watch total runtime per class before and after; the measurement above predicts parity, and a
  regression would show up as a class that grew by more than a few seconds.
