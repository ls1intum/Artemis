package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.service.FileUtilUnitTest.FILE_WITH_UNIX_LINE_ENDINGS;
import static de.tum.cit.aet.artemis.core.service.FileUtilUnitTest.exportTestRootPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.IOExceptionList;
import org.apache.commons.io.IOIndexedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FileServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ResourceLoaderService resourceLoaderService;

    @Autowired
    private FileService fileService;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    private final Path javaPath = Path.of("templates", "java", "java.txt");

    // the resource loader allows to load resources from the file system for this prefix
    private final Path overridableBasePath = Path.of("templates", "jenkins");

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(javaPath);
        RepositoryExportTestUtil.safeDeleteDirectory(overridableBasePath);
    }

    @AfterEach
    @BeforeEach
    void deleteFiles() throws IOException {
        RepositoryExportTestUtil.safeDeleteDirectory(exportTestRootPath);
    }

    @Test
    void testGetFileForPath() throws IOException {
        FileUtilUnitTest.writeFile("testFile.txt", FILE_WITH_UNIX_LINE_ENDINGS);
        byte[] result = fileService.getFileForPath(exportTestRootPath.resolve("testFile.txt"));
        assertThat(result).containsExactly(FILE_WITH_UNIX_LINE_ENDINGS.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testGetFileFOrPath_notFound() throws IOException {
        FileUtilUnitTest.writeFile("testFile.txt", FILE_WITH_UNIX_LINE_ENDINGS);
        byte[] result = fileService.getFileForPath(exportTestRootPath.resolve(UUID.randomUUID() + ".txt"));
        assertThat(result).isNull();
    }

    /**
     * Reproduces the collision reported in <a href="https://github.com/ls1intum/Artemis/issues/13575">#13575</a>: student
     * repositories are exported on a pool of ten threads, so a directory name derived from the current wall clock is handed
     * out more than once, and every colliding caller schedules its own recursive deletion of the shared directory.
     */
    @Test
    void testCreateTemporaryDirectory_shouldReturnDistinctDirectoriesWhenCalledConcurrently() throws IOException {
        Path parent = createTempTargetDirectory("testCreateTemporaryDirectoryConcurrently");
        final int callCount = 32;

        Set<Path> createdDirectories;
        try (var threadPool = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, callCount).mapToObj(ignored -> CompletableFuture.supplyAsync(() -> {
                try {
                    return fileService.createTemporaryDirectory(parent, "export-", 1);
                }
                catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, threadPool)).toList();
            createdDirectories = futures.stream().map(CompletableFuture::join).collect(Collectors.toSet());
        }

        assertThat(createdDirectories).hasSize(callCount).allSatisfy(directory -> assertThat(directory).isDirectory());
    }

    /**
     * The pending deletions used to be kept in a map keyed by path, so scheduling a second cleanup for the same path
     * dropped the first future. That task then ran anyway but could no longer be cancelled at shutdown. Two schedules for
     * one path must therefore leave two cancellable futures behind, and neither of them may report an error when the
     * other one got there first.
     */
    @Test
    void testScheduleDirectoryPathForRecursiveDeletion_shouldTrackEveryScheduleForTheSamePath() throws Exception {
        // Its own instance, because the shared bean's tracked deletions are whatever the rest of the context scheduled.
        var isolatedFileService = new FileService(null, tempFileUtilService);
        Path directory = createTempTargetDirectory("testDuplicateDeletionSchedules");

        // A long delay keeps both cleanups pending, so the tracking itself can be observed rather than its outcome.
        isolatedFileService.scheduleDirectoryPathForRecursiveDeletion(directory, 60);
        isolatedFileService.scheduleDirectoryPathForRecursiveDeletion(directory, 60);

        var trackedDeletions = isolatedFileService.pendingDeletions();
        // Keyed by path, the second schedule replaced the first, leaving one entry and one task nobody could cancel.
        assertThat(trackedDeletions).as("both cleanups for the same path have to stay cancellable").hasSize(2);

        isolatedFileService.destroy();
        assertThat(trackedDeletions).as("shutdown has to cancel every tracked cleanup, not just the last one").allMatch(Future::isCancelled);
        assertThat(directory).as("a cancelled cleanup must not have run").exists();
    }

    @Test
    void testScheduleDirectoryPathForRecursiveDeletion_shouldRemoveTheDirectory() throws IOException {
        Path directory = createTempTargetDirectory("testScheduledDeletionRemovesDirectory");

        assertThatNoException().isThrownBy(() -> fileService.scheduleDirectoryPathForRecursiveDeletion(directory, 0));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(directory).doesNotExist());
    }

    /**
     * The failure in <a href="https://github.com/ls1intum/Artemis/issues/13575">#13575</a> is a directory walk tripping
     * over a file another cleanup had already removed. The root directory is still present at that point, so recognising
     * it means inspecting the cause chain Commons IO reports, not just checking whether the target still exists.
     */
    @Test
    void testScheduleDirectoryPathForRecursiveDeletion_shouldNotReportAConcurrentlyRemovedEntryAsAnError() {
        // Rebuilds the exact chain from the issue: IOExceptionList -> IOIndexedException -> FileNotFoundException ->
        // NoSuchFileException. Provoking the race for real would be timing dependent, and the point of the check is
        // precisely that it reads this chain rather than looking at the file system.
        var vanishedEntry = "/opt/artemis/data/repos-download/1787748504957/TESTBENCHMARKING1GROUP1EXERCISE1/.git/refs/tags";
        var missingFile = new NoSuchFileException(vanishedEntry);
        var cannotDelete = new FileNotFoundException("Cannot delete file: " + vanishedEntry);
        cannotDelete.initCause(missingFile);
        var reported = new IOExceptionList(List.of(new IOIndexedException(0, cannotDelete)));

        assertThat(FileService.isCausedByMissingFile(reported)).as("a vanished entry inside the tree must be recognised as benign").isTrue();
        assertThat(FileService.isCausedByMissingFile(new IOException("disk is full"))).as("an unrelated failure must still be reported").isFalse();

        // A walk can collect several failures at once. One vanished file next to a real problem must not hide it.
        var mixed = new IOExceptionList(List.of(new IOIndexedException(0, cannotDelete), new IOIndexedException(1, new AccessDeniedException("/opt/artemis/data/locked"))));
        assertThat(FileService.isCausedByMissingFile(mixed)).as("a permission failure alongside a vanished file must still be reported").isFalse();

        assertThat(FileService.isCausedByMissingFile(new IOExceptionList(List.of()))).as("an aggregate without any cause says nothing, so it is not benign").isFalse();
    }

    @Test
    void testCreateTemporaryDirectory_shouldCreateTheDirectoryAndScheduleItForDeletion() throws IOException {
        Path parent = createTempTargetDirectory("testCreateTemporaryDirectory");

        Path temporaryDirectory = fileService.createTemporaryDirectory(parent, "export-", 1);

        assertThat(temporaryDirectory).isDirectory().hasParent(parent);
        assertThat(temporaryDirectory.getFileName().toString()).startsWith("export-");
        verify(fileService).scheduleDirectoryPathForRecursiveDeletion(temporaryDirectory, 1L);
    }

    @Test
    void testCopyResourceKeepDirectories() throws IOException {
        Path targetDir = createTempTargetDirectory("testCopyResourceKeepDirectories");
        final Resource[] resources = { resourceLoaderService.getResource(javaPath) };

        FileUtil.copyResources(resources, Path.of("templates"), targetDir, true);

        final Path expectedTargetFile = targetDir.resolve("java").resolve("java.txt");
        assertThat(expectedTargetFile).exists().isNotEmptyFile();
    }

    @Test
    void testCopyResourceDoNotKeepDirectory() throws IOException {
        Path targetDir = createTempTargetDirectory("testCopyResourceDoNotKeepDirectory");
        final Resource[] resources = { resourceLoaderService.getResource(javaPath) };

        FileUtil.copyResources(resources, Path.of("templates"), targetDir, false);

        final Path expectedTargetFile = targetDir.resolve("java.txt");
        assertThat(expectedTargetFile).exists().isNotEmptyFile();
    }

    @Test
    void testCopyResourceRemovePrefix() throws IOException {
        Path targetDir = createTempTargetDirectory("testCopyResourceRemovePrefix");
        final Resource[] resources = { resourceLoaderService.getResource(javaPath) };

        FileUtil.copyResources(resources, Path.of("templates", "java"), targetDir, true);

        final Path expectedTargetFile = targetDir.resolve("java.txt");
        assertThat(expectedTargetFile).exists().isNotEmptyFile();
    }

    private Path createTempTargetDirectory(String prefix) throws IOException {
        return tempFileUtilService.createTempDirectory(prefix);
    }

    @Test
    void testIgnoreDirectoryFalsePositives() throws IOException {
        Path targetDir = createTempTargetDirectory("testIgnoreDirectoryFalsePositives");
        final Path sourceDirectory = overridableBasePath.resolve("package.xcworkspace");
        Files.createDirectories(sourceDirectory);

        final Resource[] resources = resourceLoaderService.getFileResources(overridableBasePath);
        assertThat(resources).isNotEmpty();

        FileUtil.copyResources(resources, Path.of("templates"), targetDir, true);

        final Path expectedTargetFile = targetDir.resolve("jenkins").resolve("package.xcworkspace");
        assertThat(expectedTargetFile).doesNotExist();
    }
}
