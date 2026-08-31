# Repository export: remove timestamp temp paths, stream zips from bare repos

Issue: [ls1intum/Artemis#13575](https://github.com/ls1intum/Artemis/issues/13575)

## Problem

`FileService.getTemporaryUniquePathWithoutPathCreation` names a temporary directory after
`System.currentTimeMillis()`. `ProgrammingExerciseExportService.exportStudentRepositories` runs the
per-participation work on a fixed pool of ten threads, and every task calls that helper, so up to ten
participations receive the same path.

Consequences:

1. **N cleanup tasks for one directory.** Each colliding call schedules its own recursive deletion. Five
   minutes later they run concurrently and `FileUtils.deleteDirectory` walks a tree another task is already
   unlinking, producing the reported `NoSuchFileException`.
2. **A leaked future.** `FileService.futures` is keyed by path, so a duplicate `put` drops the earlier
   future. `destroy()` can no longer cancel that task, but it still fires.
3. **A latent corruption path.** Two exports touching the same participation concurrently resolve the same
   clone target, and `getOrCheckoutRepository` deletes the target directory before cloning, so one export
   can wipe the other's checkout mid-flight.
4. **The same flaw in a second helper.** `FileUtil.getUniqueSubfolderPath` (plagiarism detection, data
   export, submission export) collides identically and, on collision, returns an existing path without
   creating it, so callers silently share a directory.

A second, independent problem sits in the same code. Course archiving exports every student repository
with all `RepositoryExportOptionsDTO` flags false, yet still performs a `file://` clone (pack plus working
tree), a `pull` on the fresh clone, a full `FileUtils.copyDirectory` into the export directory, and finally
a zip pass over the copy: roughly four full-size writes and four reads per repository. None of the options
that need a working tree are set.

## Approach

### Temporary directories

`TempFileUtilService.createTempDirectory(parent, prefix)` already wraps `Files.createTempDirectory`, which
is atomic and unique by construction, and `TempFileArchitectureTest` forbids anyone else from calling it
directly. The two hand-rolled helpers predate it.

Replace both with a single method that pairs creation with cleanup, since `FileService` owns the deletion
scheduler:

```java
public Path createTemporaryDirectory(Path parent, String prefix, long deleteDelayInMinutes) throws IOException
```

Drop `getTemporaryUniquePathWithoutPathCreation`, `getTemporaryUniqueSubfolderPath` and
`FileUtil.getUniqueSubfolderPath`. The "without path creation" contract is unnecessary: at every call site
the directory is either a plain output directory, a clone *parent* (`targetPath.resolve(projectKey/slug)`),
or a clone target that `getOrCheckoutRepository` deletes before cloning.

Give each export operation one root and each repository a deterministic subdirectory inside it
(`workingRoot.resolve(participationId)`), so one directory and one cleanup task exist per export instead of
per repository. The collision cannot recur even if the naming were reverted, and a partially failed export
cleans up completely.

### FileService hardening

- `futures` becomes a `Set<ScheduledFuture<?>>`. The path key bought nothing and silently dropped futures.
- A scheduled deletion whose target has already vanished logs at debug, not error. A best-effort cleanup
  racing another cleanup is not an error.

### Streaming export from bare repositories

`GitRepositoryExportService` and `InMemoryRepositoryBuilder` already export a bare LocalVC repository to a
zip with no clone and no checkout, and the single-repository REST downloads use them. The bulk and archival
paths never adopted them.

`InMemoryRepositoryBuilder`:

- Pack directly from the bare repository. The current `Transport.open(...).fetch(...)` into an in-memory
  DFS repository copies every object through the heap for no benefit, since the source is a local bare repo.
- Add `writeZip(Repository, OutputStream)`; `buildZip` delegates for callers that need `contentLength`.
- Stop writing `remote.origin.url = file:///.../local-vc/...` into the exported `.git/config`. That leaks
  the server's internal filesystem layout into every instructor zip downloaded today, and the path is
  useless on a client machine.

`GitRepositoryExportService`:

```java
public enum RepositoryExportContent { WORKING_TREE_ONLY, WITH_HISTORY }

public Path exportRepositoryToZipFile(VcsRepositoryUri uri, Path targetDirectory, String zipFilename,
        RepositoryExportContent content) throws IOException
```

`WITH_HISTORY` uses `writeZip`; `WORKING_TREE_ONLY` uses JGit's `ArchiveCommand`. Both write through
`Files.newOutputStream` straight into the destination directory. A repository that cannot be read — because
its `HEAD` does not resolve, or because it was never created — is reported as an export error instead of
producing today's silent zero-byte zip, and the partially written file is removed, since the callers zip
whole directories and an unreadable archive inside one of them hides the failure. The duplicated
student-repository naming in `getRepositoryWithParticipation` and `exportStudentRepositoryInMemory` moves
into one helper.

Removing the clone from the instructor path leaves `getRepositoryWithParticipation`'s `zipOutput` branch, the
`zipFiles` helper it was the only caller of, and the service's `ZipFileService` dependency unreachable; all
three go.

### Wiring

`ProgrammingExerciseExportService`:

- Instructor and auxiliary repositories always use `exportRepositoryToZipFile(..., WITH_HISTORY)`.
  `createZipForRepository` is deleted; this path no longer clones.
- Student repositories use `exportRepositoryToZipFile(..., WORKING_TREE_ONLY)` when none of
  `filterLateSubmissions`, `addParticipantName`, `combineStudentCommits`, `anonymizeRepository` or
  `normalizeCodeStyle` is set. Otherwise the existing clone path runs unchanged.

The export dialog defaults to `combineStudentCommits: true` plus `addParticipantName` (instructors) or
`anonymizeRepository` (tutors), so the manual export always keeps its checkout. The fast path therefore
covers course and exam archiving and the exercise Material export, and never touches the anonymization or
commit-combining logic.

Per archived repository this replaces clone, directory copy and zip with one read of the bare pack and one
compressed write.

## Behaviour changes

- Student repositories inside a course archive become one zip per repository instead of a directory tree,
  and contain the working tree only. This matches the single-repository student download, which already
  snapshots to exclude `.git` for privacy, and `extractZipFileRecursively` already handles the nesting.
- Instructor, solution, test and auxiliary repositories keep their full history.
- Exported `.git/config` no longer names an internal server path.

## Out of scope

- `filterLateSubmissions` alone is expressible as archive-at-commit, but the dialog never sends it alone,
  so it stays on the clone path.
- The anonymization and commit-combining implementations are untouched.

## Tests

- N parallel `createTemporaryDirectory` calls on one parent yield N distinct existing directories.
- Duplicate deletion schedules for one path leave both futures cancellable and log no error.
- `WITH_HISTORY` produces `.git/HEAD`, a pack, and no `file://` remote in `.git/config`;
  `WORKING_TREE_ONLY` produces no `.git`; an unborn repository reports an error rather than an empty zip.
- Archiving an exercise with several participations schedules one cleanup per export and yields one zip per
  repository. Existing `scheduleDirectoryPathForRecursiveDeletion` call-count expectations in
  `ProgrammingExerciseLocalVCJenkinsIntegrationTest` drop accordingly.
