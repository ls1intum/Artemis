package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.service.FileUtilUnitTest.FILE_WITH_UNIX_LINE_ENDINGS;
import static de.tum.cit.aet.artemis.core.service.FileUtilUnitTest.exportTestRootPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
