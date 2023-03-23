package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

class ZipFileServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ZipFileService zipFileService;

    @Test
    void testExtractZipFileRecursively_unzipsNestedZipCorrectly() throws IOException {
        Path testDir = Path.of("test-dir");
        if (!Files.exists(Path.of("test-dir"))) {
            Files.createDirectory(Path.of("test-dir"));
        }
        Path rootDir = Files.createTempDirectory(Path.of("test-dir"), "root-dir");
        Path subDir = Files.createTempDirectory(rootDir, "sub-dir");
        Path subDir2 = Files.createTempDirectory(subDir, "sub-dir2");
        Path file1 = Files.createTempFile(rootDir, "file1", ".json");
        Path file2 = Files.createTempFile(subDir2, "file2", ".json");

        Path p = Files.createTempDirectory(Path.of("test-dir"), "output");
        zipFileService.createZipFile(p.resolve("zip-dir.zip"), List.of(subDir, subDir2), testDir);

        zipFileService.extractZipFileRecursively(p.resolve("zip-dir.zip"));
    }
}
