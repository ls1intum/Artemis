package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;

import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ZipFileServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ZipFileService zipFileService;

    @Test
    void testExtractZipFileRecursively_unzipsNestedZipCorrectly() throws IOException {
        Path testDir = Files.createTempDirectory(tempPath, "test-dir");
        Path zipDir = Files.createTempDirectory(tempPath, "zip-dir");
        Path rootDir = Files.createTempDirectory(testDir, "root-dir");
        Path subDir = Files.createTempDirectory(rootDir, "sub-dir");
        Path subDir2 = Files.createTempDirectory(subDir, "sub-dir2");
        Path file1 = Files.createTempFile(rootDir, "file1", ".json");
        Path file2 = Files.createTempFile(subDir2, "file2", ".json");
        Path zipFile = Files.createTempFile(zipDir, "abc", ".zip");
        Path zippedFile = zipFileService.createZipFileWithFolderContent(zipFile, testDir, null);
        zipFileService.extractZipFileRecursively(zippedFile);

        Path extractedZipFilePath = zippedFile.getParent().resolve(zippedFile.getFileName().toString().replace(".zip", ""));
        Path rootDirPathInZip = extractedZipFilePath.resolve(rootDir.getFileName());
        Path subDirPathInZip = extractedZipFilePath.resolve(rootDir.getFileName()).resolve(subDir.getFileName());
        assertThat(extractedZipFilePath).isDirectoryContaining(Predicate.isEqual(rootDirPathInZip));
        assertThat(rootDirPathInZip).isDirectoryContaining(Predicate.isEqual(subDirPathInZip));
        assertThat(subDirPathInZip).isDirectoryContaining(Predicate.isEqual(subDirPathInZip.resolve(subDir2.getFileName())));
        assertThat(subDirPathInZip.resolve(subDir2.getFileName()))
                .isDirectoryContaining(Predicate.isEqual(subDirPathInZip.resolve(subDir2.getFileName()).resolve(file2.getFileName())));
        assertThat(rootDirPathInZip).isDirectoryContaining(Predicate.isEqual(rootDirPathInZip.resolve(file1.getFileName())));
    }

    @Test
    void testCreateTemporaryZipFileSchedulesFileForDeletion() throws IOException {
        var tempZipFile = Files.createTempFile(tempPath, "test", ".zip");
        zipFileService.createTemporaryZipFile(tempZipFile, List.of(), 5);
        assertThat(tempZipFile).exists();
        verify(fileService).schedulePathForDeletion(tempZipFile, 5L);
    }

    @Test
    void testCreateZipFileWithFolderContentInMemory() throws Exception {
        Path testDir = Files.createTempDirectory(tempPath, "test-dir");
        Path testFile = Files.createTempFile(testDir, "test", ".txt");
        FileUtils.writeByteArrayToFile(testFile.toFile(), "test content".getBytes());

        ByteArrayResource result = zipFileService.createZipFileWithFolderContentInMemory(testDir, "test-archive.zip", null);

        assertThat(result).isNotNull();
        assertThat(result.getFilename()).isEqualTo("test-archive.zip");
        assertThat(result.contentLength()).isGreaterThan(0);

        ZipTestUtil.verifyZipStructureAndContent(result.getByteArray());
    }

}
