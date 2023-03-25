package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

class ZipFileServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ZipFileService zipFileService;

    @Test
    void testExtractZipFileRecursively_unzipsNestedZipCorrectly() throws IOException {

        Path testDir = Files.createTempDirectory("test-dir");
        Path zipDir = Files.createTempDirectory("zip-dir");

        Path rootDir = Files.createTempDirectory(testDir, "root-dir");
        Path subDir = Files.createTempDirectory(rootDir, "sub-dir");
        Path subDir2 = Files.createTempDirectory(subDir, "sub-dir2");
        Path file1 = Files.createTempFile(rootDir, "file1", ".json");
        Path file2 = Files.createTempFile(subDir2, "file2", ".json");
        Path zipFile = Files.createTempFile(zipDir, "abc", ".zip");
        Path p = zipFileService.createZipFileWithFolderContent(zipFile, testDir, null);
        zipFileService.extractZipFileRecursively(p);

        Path extractedZipFilePath = p.getParent().resolve(p.getFileName().toString().replace(".zip", ""));
        Path rootDirPathInZip = extractedZipFilePath.resolve(rootDir.getFileName());
        Path subDirPathInZip = extractedZipFilePath.resolve(rootDir.getFileName()).resolve(subDir.getFileName());
        assertThat(Files.exists(rootDirPathInZip)).isTrue();
        assertThat(Files.exists(subDirPathInZip)).isTrue();
        assertThat(Files.exists(subDirPathInZip.resolve(subDir2.getFileName()))).isTrue();
        assertThat(Files.exists(rootDirPathInZip.resolve(file1.getFileName()))).isTrue();
        assertThat(Files.exists(subDirPathInZip.resolve(subDir2.getFileName()).resolve(file2.getFileName()))).isTrue();

    }
}
