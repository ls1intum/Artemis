package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assumptions;
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
        Path nestedZipFile = testDir.resolve("inner.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(nestedZipFile))) {
            zipOutputStream.putNextEntry(new ZipEntry("inner-dir/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("inner-dir/inner.txt"));
            zipOutputStream.write("nested".getBytes());
            zipOutputStream.closeEntry();
        }
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
        Path extractedNestedZipDir = extractedZipFilePath.resolve("inner").resolve("inner-dir");
        assertThat(extractedNestedZipDir).isDirectory();
        assertThat(Files.readString(extractedNestedZipDir.resolve("inner.txt"))).isEqualTo("nested");
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
        Path emptyDir = tempFileUtilService.createTempDirectory(contentDir, "empty-dir");
        Path ignoredStandaloneFile = sourceDir.resolve("gc.log.lock");
        FileUtils.writeByteArrayToFile(ignoredStandaloneFile.toFile(), "ignored".getBytes());
        Path ignoredNestedFile = contentDir.resolve("gc.log.lock");
        FileUtils.writeByteArrayToFile(ignoredNestedFile.toFile(), "ignored".getBytes());

        Path zipOutDir = tempFileUtilService.createTempDirectory("create-zip-out");
        Path zipFilePath = zipOutDir.resolve("archive.zip");
        zipFileService.createZipFile(zipFilePath, List.of(standaloneFile, ignoredStandaloneFile, contentDir));

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
        // empty directories must not disappear when replacing zip4j's addFolder behavior
        assertThat(entryNames).contains(contentDir.getFileName() + "/" + contentDir.relativize(emptyDir) + "/");
        assertThat(entryNames).doesNotContain(ignoredStandaloneFile.getFileName().toString(), contentDir.getFileName() + "/" + ignoredNestedFile.getFileName());
    }

    /**
     * A repository exported this way carries an executable {@code gradlew}. Storing it without its permissions makes
     * the extracted file non-executable, and because git tracks the executable bit, every extracted student repository
     * reports a spurious modification before anyone has touched it.
     */
    @Test
    void testCreateZipFile_keepsTheExecutableBit() throws IOException {
        // Windows has no POSIX view, so the file system cannot carry the permissions this test is about.
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "The file system does not support POSIX permissions");
        Path contentDir = tempFileUtilService.createTempDirectory("executable-content");
        Path executableFile = contentDir.resolve("gradlew");
        FileUtils.writeStringToFile(executableFile.toFile(), "#!/bin/sh\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(executableFile, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path regularFile = contentDir.resolve("build.gradle");
        FileUtils.writeStringToFile(regularFile.toFile(), "plugins {}\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(regularFile, PosixFilePermissions.fromString("rw-r--r--"));

        Path zipFilePath = tempFileUtilService.createTempDirectory("executable-zip").resolve("repository.zip");
        zipFileService.createZipFile(zipFilePath, List.of(contentDir));

        String prefix = contentDir.getFileName().toString() + "/";
        assertThat(unixModeOf(zipFilePath, prefix + "gradlew")).as("the executable bit of gradlew must survive the export").isEqualTo(0755);
        assertThat(unixModeOf(zipFilePath, prefix + "build.gradle")).as("a regular file must not become executable").isEqualTo(0644);
    }

    /**
     * A course archive nests one ZIP per repository inside the archive it hands out. Deflating those again measured a
     * 2.5% gain against 50% for the plain CSV files, so the outer pass compresses the entire payload for almost nothing.
     * Already-compressed entries are therefore stored, and everything else stays deflated.
     */
    @Test
    void testCreateZipFile_storesAlreadyCompressedEntriesInsteadOfDeflatingThemAgain() throws Exception {
        Path sourceDir = tempFileUtilService.createTempDirectory("stored-src");
        Path nestedZip = sourceDir.resolve("repository.zip");
        FileUtils.writeByteArrayToFile(nestedZip.toFile(), ZipTestUtil.createTestZipFile(Map.of("src/Main.java", "public class Main {}")));
        Path report = sourceDir.resolve("report.csv");
        FileUtils.writeStringToFile(report.toFile(), "id,type\n1,ProgrammingExercise\n".repeat(50), StandardCharsets.UTF_8);

        Path zipFilePath = tempFileUtilService.createTempDirectory("stored-out").resolve("archive.zip");
        zipFileService.createZipFile(zipFilePath, List.of(nestedZip, report));

        try (org.apache.commons.compress.archivers.zip.ZipFile zipFile = org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(zipFilePath).get()) {
            ZipArchiveEntry nestedEntry = zipFile.getEntry("repository.zip");
            assertThat(nestedEntry).isNotNull();
            assertThat(nestedEntry.getMethod()).as("an already compressed entry must be stored, not deflated again").isEqualTo(ZipEntry.STORED);
            assertThat(nestedEntry.getSize()).as("a stored entry still has to record its size").isEqualTo(Files.size(nestedZip));

            ZipArchiveEntry reportEntry = zipFile.getEntry("report.csv");
            assertThat(reportEntry).isNotNull();
            assertThat(reportEntry.getMethod()).as("compressible content must still be deflated").isEqualTo(ZipEntry.DEFLATED);
            assertThat(reportEntry.getCompressedSize()).as("the plain file must actually get smaller").isLessThan(reportEntry.getSize());

            // Storing must not corrupt anything: the nested archive has to come back byte for byte.
            try (var stream = zipFile.getInputStream(nestedEntry)) {
                assertThat(stream.readAllBytes()).isEqualTo(Files.readAllBytes(nestedZip));
            }
        }
    }

    /**
     * Returns the unix permission bits an entry was stored with, or 0 when the zip records none.
     */
    private static int unixModeOf(Path zipFilePath, String entryName) throws IOException {
        try (org.apache.commons.compress.archivers.zip.ZipFile zipFile = org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(zipFilePath).get()) {
            ZipArchiveEntry entry = zipFile.getEntry(entryName);
            assertThat(entry).as("entry %s must be in the archive", entryName).isNotNull();
            return entry.getUnixMode() & 0777;
        }
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
