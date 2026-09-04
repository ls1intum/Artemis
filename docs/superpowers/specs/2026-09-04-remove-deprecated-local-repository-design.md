# Removing the deprecated `LocalRepository` test helper

Status: approved design, ready for an implementation plan
Date: 2026-09-04
Branch: `chore/remove-deprecated-local-repository`

## Problem

`de.tum.cit.aet.artemis.programming.util.LocalRepository` and
`de.tum.cit.aet.artemis.programming.util.LocalRepositoryUriUtil` are marked `@Deprecated`
with the note "DO NOT USE this class anymore for server tests". They date from the era when
creating a repository in an external version control system was expensive, so tests mocked a
remote. With LocalVC and LocalCI as the default modules a test can create a real repository
directly, and mocking a remote buys nothing.

`LocalRepository` today serves two unrelated purposes, and only one of them is obsolete.

### Mode A — a real LocalVC repository (keep the behaviour, replace the type)

`configureRepos(basePath, workingCopyName, originRepositoryFolder)` initialises a bare
repository at a caller-supplied folder. Every caller reaches it through
`LocalVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug)`,
which passes `localVCBasePath/PROJECT_KEY/slug.git` — the exact path
`LocalVCRepositoryUri.getLocalRepositoryPath` resolves to. The "origin" is therefore the real
LocalVC repository; nothing is mocked. `LocalRepository` is only a mutable handle holding the
bare repository, the working copy, and their `Git` objects.

About 31 test classes and ~340 member accesses use this mode.

### Mode B — a fabricated remote (delete)

`configureRepos(basePath, "someLocalRepo", "someOriginRepo")` creates a scratch bare repository
under a random folder that LocalVC knows nothing about. `LocalRepositoryUriUtil` then fabricates
a URI by splicing a `git` segment into the path, purely so `LocalVCRepositoryUri`'s validation
accepts it:

```java
var uri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(repo.workingCopyGitRepoFile, localVCBasePath));
```

The resulting participation points at a `file://` path that is not a LocalVC repository. This is
the mock that has to go. After the dead code below is removed it survives in five places.

### Dead code found during the survey

- `ProgrammingUtilTestService` (177 lines). Injected into `ProgrammingExerciseIntegrationTestService`
  but no method is ever called. All three of its methods use the fabricated-URI trick.
- `ProgrammingExerciseUtilService.createGitRepository()`. Creates a repository, then builds a
  `mock(Repository.class)`, stubs two methods on it, and discards it. Both callers
  (`AthenaRepositoryExportServiceTest`) get no effect from it.
- `ParticipationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo` keeps two
  legitimate callers (`ExamUtilService`, `DataExportCreationServiceTest`) that pass real LocalVC
  URIs, so it stays.

### Latent bug the refactoring fixes

`ContinuousIntegrationTestService:48` and `AthenaRepositoryExportServiceTest:57` construct
`new LocalRepository(defaultBranch)` in a **field initialiser**, so the `@Value`-injected
`defaultBranch` is still `null`. JGit then falls back to `master` and writes a
`refs/heads/null` symref. The tests only pass because they never depend on the branch. Creating
repositories exclusively through `LocalVCLocalCITestService`, which reads `defaultBranch` after
injection, removes the whole class of mistake.

## Design

### 1. `LocalVCTestRepository` replaces `LocalRepository`

New immutable record, `de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository`:

```java
public record LocalVCTestRepository(String projectKey, String repositorySlug,
        Path bareRepositoryPath, Git bareRepository,
        Path workingCopyPath, Git workingCopy) implements AutoCloseable {

    public List<RevCommit> workingCopyCommits() throws GitAPIException;
    public List<RevCommit> bareRepositoryCommits() throws GitAPIException;

    /** Closes both Git handles. */
    @Override public void close();

    /** Closes both handles and deletes both directories. Replaces resetLocalRepo(). */
    public void deleteRepositories() throws IOException;
}
```

Member renames applied at every call site:

| `LocalRepository` | `LocalVCTestRepository` |
| --- | --- |
| `workingCopyGitRepoFile` (`File`) | `workingCopyPath()` (`Path`) |
| `workingCopyGitRepo` (`Git`) | `workingCopy()` |
| `remoteBareGitRepoFile` (`File`) | `bareRepositoryPath()` (`Path`) |
| `remoteBareGitRepo` (`Git`) | `bareRepository()` |
| `resetLocalRepo()` | `deleteRepositories()` |
| `getAllLocalCommits()` | `workingCopyCommits()` |
| `getAllOriginCommits()` | `bareRepositoryCommits()` |

`remoteBare*` becomes `bare*` because the repository is not a remote — it is the LocalVC
repository the server itself serves. `File` becomes `Path`, removing roughly 50 `.toPath()`
calls at call sites.

The record carries `projectKey` and `repositorySlug`, which `ProgrammingExerciseTestService`
currently tracks in a side `IdentityHashMap<LocalRepository, RepositoryMetadata>`. That map is
deleted.

### 2. Construction is restricted to the LocalVC layout

The factory is
`LocalVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug)`,
which returns a `LocalVCTestRepository` whose bare repository always sits at
`localVCBasePath/PROJECT_KEY/slug.git`. `RepositoryExportTestUtil.cloneExistingBareRepo` builds
the record directly, but only around a bare repository that already exists at that path. The
record deliberately exposes no way to derive a repository URI from a filesystem path — that is
what made mode B possible — so a URI always comes from `buildLocalVCUri` or
`new LocalVCRepositoryUri(baseUri, projectKey, slug)`.

Two static initialisation helpers move onto the record so the current static users keep working:

- `LocalVCTestRepository.initializeBareRepository(Path, String defaultBranch)` — used by
  `ParticipationUtilService.ensureLocalVcRepositoryExists` when the `LocalVCLocalCITestService`
  bean is unavailable.
- `LocalVCTestRepository.initializeWorkingRepository(Path, String defaultBranch)` — used by
  `ProgrammingExerciseGitIntegrationTest:65`.

Both keep the JGit configuration from `LocalRepository.initialize` verbatim (autogc off,
symlinks off, gpgsign off, branch tracking). That configuration exists to prevent flakiness
(see PR #13361) and must not be simplified in this change.

### 3. `LocalRepositoryUriUtil` is deleted with no replacement

Nothing takes its place. Tests that need a repository URI use
`LocalVCLocalCITestService.buildLocalVCUri(user, password, projectKey, slug)` or
`new LocalVCRepositoryUri(localVCBaseUri, projectKey, slug)`, both of which describe a
repository the server can actually serve.

### 4. Rewriting the five mode-B call sites

| File | Now | After |
| --- | --- | --- |
| `AthenaInternalResourceIntegrationTest` | scratch repo + 3× `copyBareRepositoryWithoutHistory` into template/solution/tests | seed the three real LocalVC repositories directly via `RepositoryExportTestUtil.seedBareRepository`; keep the `README.md` = `Initial commit` assertion |
| `AthenaResourceIntegrationTest` | same shape | same replacement |
| `AthenaRepositoryExportServiceTest` | scratch repo written to but never committed, never wired; `createGitRepository()` mock; assertions are bare `isNotNull()` | wire template/solution/tests through `RepositoryExportTestUtil.createAndWireBaseRepositories`, seed a known file, and assert the exported map contains it |
| `ContinuousIntegrationTestService` | scratch repo + fabricated URI + `addStudentParticipationForProgrammingExerciseForLocalRepo` | `participationUtilService.addStudentParticipationForProgrammingExercise` (which already creates the LocalVC repository) plus `RepositoryExportTestUtil.getOrCreateWorkingCopyForParticipation` for the file and folder |
| `ProgrammingExerciseIntegrationTestService` | `studentRepository1/2` as scratch repos, unrelated to `participation1/2` | working copies of the real repositories behind `participation1`/`participation2` |
| `ProgrammingExerciseResourceTest` | scratch repo used only as a content source for `seedLocalVcBareFrom` | `RepositoryExportTestUtil.seedBareRepository(..., projectKey, templateSlug, git -> …)` writing content straight into the real template repository |

`AthenaRepositoryExportServiceTest.shouldExportRepository` also sets
`participation.setRepositoryUri("git://test")`. That participation is exported by
`getStudentRepositoryFilesContent`, so it gets a real LocalVC student repository too.

### 5. Names that no longer tell the truth

`ProgrammingExerciseTestService.setupRepositoryMocks*` mocks nothing — it creates real
repositories. Rename to `setupRepositories`, `setupRepositoriesForParticipant`. The four external
call sites (`StudentExamIntegrationTest` ×2, `ProgrammingExamIntegrationTest` ×2) follow.

## Out of scope

- **Where working copies live.** They are created under
  `localVCBasePath/<random6>/<random6>-<name>.git`, i.e. inside the LocalVC storage root. Moving
  them under `artemis.temp-path` is cleaner but changes storage layout for ~31 test classes and
  is an independent risk. Deleting `LocalRepositoryUriUtil` already closes the loophole that made
  the placement exploitable.
- The `mock(Repository.class)` uses in `PlagiarismDetectionServiceTest`,
  `AbstractArtemisBuildAgentTest` and `ProgrammingExerciseRepositoryServiceTest`, and the
  `GitService` stubs in the Hyperion and Deimos unit tests. None of them involve
  `LocalRepository`; they are unit-test doubles for services, not fabricated remotes.
- Production code. This change touches `src/test` only.

## Implementation stages

Each stage leaves the test tree compiling and is a separate commit.

**S0 — remove dead code.** Delete `ProgrammingUtilTestService`, its `@Autowired` field and import
in `ProgrammingExerciseIntegrationTestService`, `ProgrammingExerciseUtilService.createGitRepository()`
and its two call sites. Removes three mode-B call sites and one discarded Mockito mock.

**S1 — rewrite the remaining mode-B call sites** (section 4). At the end of this stage
`LocalRepositoryUriUtil` and the two `configureRepos(Path, String, String[, boolean])` overloads
have zero callers; delete all three.

**S2 — introduce `LocalVCTestRepository`.** Add the record, change
`LocalVCLocalCITestService.createAndConfigureLocalRepository` to return it, migrate
`RepositoryExportTestUtil`, `ProgrammingExerciseTestService` (including dropping the
`repositoryMetadata` map) and the remaining ~28 files, delete `LocalRepository`, and port
`LocalRepositoryTest` to `LocalVCTestRepositoryTest` keeping both of its cases (history is
continued for an existing bare repository; an initial commit is created for a new one).

**S3 — naming and tidy-up.** Rename `setupRepositoryMocks*`, refresh javadoc that still says
"origin"/"remote"/"mock", remove imports and fields left unused.

## Verification

- `./gradlew compileTestJava -x webapp` after every stage.
- `./gradlew spotlessApply -x webapp` and `./gradlew spotlessCheck checkstyleMain -x webapp`.
- Run every touched test class with `./gradlew test --tests … -x webapp` (Docker required;
  Testcontainers PostgreSQL). The set is the ~40 classes that reference `LocalRepository`,
  `ProgrammingExerciseTestService`, or `ProgrammingUtilTestService`.
- `LocalVCTestRepositoryTest` must pass first: it pins the behaviour the record has to preserve.
