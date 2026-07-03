package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;

import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ZipFileServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ZipFileService zipFileService;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    @Test
    void testExtractZipFileRecursively_unzipsNestedZipCorrectly() throws IOException {
        Path testDir = tempFileUtilService.createTempDirectory("test-dir");
        Path zipDir = tempFileUtilService.createTempDirectory("zip-dir");
        Path rootDir = tempFileUtilService.createTempDirectory(testDir, "root-dir");
        Path subDir = tempFileUtilService.createTempDirectory(rootDir, "sub-dir");
        Path subDir2 = tempFileUtilService.createTempDirectory(subDir, "sub-dir2");
        Path file1 = tempFileUtilService.createTempFile(rootDir, "file1", ".json");
        Path file2 = tempFileUtilService.createTempFile(subDir2, "file2", ".json");
        Path zipFile = tempFileUtilService.createTempFile(zipDir, "abc", ".zip");
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
        var tempZipFile = tempFileUtilService.createTempFile("test", ".zip");
        zipFileService.createTemporaryZipFile(tempZipFile, List.of(), 5);
        assertThat(tempZipFile).exists();
        verify(fileService).schedulePathForDeletion(tempZipFile, 5L);
    }

    @Test
    void testCreateZipFileWithFolderContentInMemory() throws Exception {
        Path testDir = tempFileUtilService.createTempDirectory("test-dir");
        Path testFile = tempFileUtilService.createTempFile(testDir, "test", ".txt");
        FileUtils.writeByteArrayToFile(testFile.toFile(), "test content".getBytes());

        ByteArrayResource result = zipFileService.createZipFileWithFolderContentInMemory(testDir, "test-archive.zip", null);

        assertThat(result).isNotNull();
        assertThat(result.getFilename()).isEqualTo("test-archive.zip");
        assertThat(result.contentLength()).isGreaterThan(0);

        ZipTestUtil.verifyZipStructureAndContent(result.getByteArray());
    }

    @Test
    void testCreateZipFile_withFileAndDirectory_storesFileAtRootAndDirectoryWithPrefix() throws IOException {
        Path sourceDir = tempFileUtilService.createTempDirectory("create-zip-src");
        Path standaloneFile = tempFileUtilService.createTempFile(sourceDir, "standalone", ".txt");
        FileUtils.writeByteArrayToFile(standaloneFile.toFile(), "hello".getBytes());
        Path contentDir = tempFileUtilService.createTempDirectory(sourceDir, "content-dir");
        Path nestedFile = tempFileUtilService.createTempFile(contentDir, "nested", ".txt");
        FileUtils.writeByteArrayToFile(nestedFile.toFile(), "world".getBytes());

        Path zipOutDir = tempFileUtilService.createTempDirectory("create-zip-out");
        Path zipFilePath = zipOutDir.resolve("archive.zip");
        zipFileService.createZipFile(zipFilePath, List.of(standaloneFile, contentDir));

        assertThat(zipFilePath).exists();
        List<String> entryNames = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                entryNames.add(entries.nextElement().getName());
            }
        }
        // a single file is stored at the zip root under its file name
        assertThat(entryNames).contains(standaloneFile.getFileName().toString());
        // a directory is stored recursively with the directory name as the top-level entry
        assertThat(entryNames).contains(contentDir.getFileName().toString() + "/" + nestedFile.getFileName().toString());
    }

    @Test
    void testExtractZipFileRecursively_rejectsZipSlipEntry() throws IOException {
        Path zipDir = tempFileUtilService.createTempDirectory("zip-slip");
        Path maliciousZip = zipDir.resolve("evil.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(maliciousZip))) {
            zipOutputStream.putNextEntry(new ZipEntry("../escaped.txt"));
            zipOutputStream.write("pwned".getBytes());
            zipOutputStream.closeEntry();
        }

        assertThatExceptionOfType(IOException.class).isThrownBy(() -> zipFileService.extractZipFileRecursively(maliciousZip));
        // the traversal target must never be written outside the extraction directory
        assertThat(zipDir.resolve("escaped.txt")).doesNotExist();
    }

}
