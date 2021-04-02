package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

public class ResourceLoaderServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ResourceLoaderService resourceLoaderService;

    @AfterEach
    public void cleanup() {
        new File("templates/java/java.txt").delete();
        new File("templates/jenkins/jenkins.txt").delete();
    }

    @Test
    public void testShouldLoadJavaFileFromClasspath() throws IOException {
        FileUtils.writeStringToFile(new File("templates/java/java.txt"), "filesystem", Charset.defaultCharset());
        String fileContent = IOUtils.toString(resourceLoaderService.getResource("templates/java/java.txt").getInputStream(), Charset.defaultCharset());

        Assertions.assertEquals("classpath", fileContent.trim());
    }

    @Test
    public void testShouldLoadJenkinsFileFromFilesystem() throws IOException {
        FileUtils.writeStringToFile(new File("templates/jenkins/jenkins.txt"), "filesystem", Charset.defaultCharset());
        String fileContent = IOUtils.toString(resourceLoaderService.getResource("templates/jenkins/jenkins.txt").getInputStream(), Charset.defaultCharset());

        Assertions.assertEquals("filesystem", fileContent.trim());
    }

    @Test
    public void testShouldLoadJenkinsFileFromClasspath_IfNotPresentInFileSystem() throws IOException {
        String fileContent = IOUtils.toString(resourceLoaderService.getResource("templates/jenkins/jenkins.txt").getInputStream(), Charset.defaultCharset());

        Assertions.assertEquals("classpath", fileContent.trim());
    }
}
