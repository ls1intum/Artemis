package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.service.FileUtilUnitTest.FILE_WITH_UNIX_LINE_ENDINGS;
import static de.tum.cit.aet.artemis.core.service.FileUtilUnitTest.exportTestRootPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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

    // TODO: either rework those tests or delete them
    @Test
    void testGetUniqueTemporaryPath_shouldNotThrowException() {
        assertThatNoException().isThrownBy(() -> {
            var uniquePath = fileService.getTemporaryUniqueSubfolderPath(Path.of("some-random-path-which-does-not-exist"), 1);
            assertThat(uniquePath.toString()).isNotEmpty();
            verify(fileService).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(1L));
        });
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
        return Files.createTempDirectory(tempPath, prefix);
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
