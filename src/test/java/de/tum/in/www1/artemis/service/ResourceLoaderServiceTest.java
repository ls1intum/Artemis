package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

class ResourceLoaderServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ResourceLoaderService resourceLoaderService;

    private final Path javaPath = Path.of("templates", "java", "java.txt");

    private final Path jenkinsPath = Path.of("templates", "jenkins", "jenkins.txt");

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(javaPath);
        Files.deleteIfExists(jenkinsPath);
    }

    @Test
    void testShouldLoadJavaFileFromClasspath() throws IOException {
        FileUtils.writeStringToFile(javaPath.toFile(), "filesystem", Charset.defaultCharset());
        try (InputStream inputStream = resourceLoaderService.getResource(javaPath.toString()).getInputStream()) {
            String fileContent = IOUtils.toString(inputStream, Charset.defaultCharset());

            Assertions.assertEquals("classpath", fileContent.trim());
        }
    }

    @Test
    void testShouldLoadJenkinsFileFromFilesystem() throws IOException {
        FileUtils.writeStringToFile(jenkinsPath.toFile(), "filesystem", Charset.defaultCharset());
        try (InputStream inputStream = resourceLoaderService.getResource(jenkinsPath.toString()).getInputStream()) {
            String fileContent = IOUtils.toString(inputStream, Charset.defaultCharset());

            Assertions.assertEquals("filesystem", fileContent.trim());
        }
    }

    @Test
    void testShouldLoadJenkinsFileFromClasspath_IfNotPresentInFileSystem() throws IOException {
        try (InputStream inputStream = resourceLoaderService.getResource(jenkinsPath.toString()).getInputStream()) {
            String fileContent = IOUtils.toString(inputStream, Charset.defaultCharset());

            Assertions.assertEquals("classpath", fileContent.trim());
        }
    }
}
