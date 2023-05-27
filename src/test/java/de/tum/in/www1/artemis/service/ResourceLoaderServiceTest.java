package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

class ResourceLoaderServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ResourceLoaderService resourceLoaderService;

    private final Path javaPath = Path.of("templates", "java", "java.txt");

    private final Path jenkinsPath = Path.of("templates", "jenkins", "jenkins.txt");

    private final List<Path> jenkinsFilesystemPaths = List.of(Path.of("templates", "jenkins", "p1.txt"), Path.of("templates", "jenkins", "p2.txt"));

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(javaPath);
        Files.deleteIfExists(jenkinsPath);
    }

    @Test
    void testShouldNotAllowAbsolutePathsSingleResource() {
        final Path path = javaPath.toAbsolutePath();
        assertThatThrownBy(() -> resourceLoaderService.getResource(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testShouldNotAllowAbsolutePathsMultipleResources() {
        final Path path = javaPath.toAbsolutePath();
        assertThatThrownBy(() -> resourceLoaderService.getResources(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testShouldLoadJavaFileFromClasspath() throws IOException {
        FileUtils.writeStringToFile(javaPath.toFile(), "filesystem", Charset.defaultCharset());
        try (InputStream inputStream = resourceLoaderService.getResource(javaPath).getInputStream()) {
            String fileContent = IOUtils.toString(inputStream, Charset.defaultCharset());

            assertThat(fileContent.trim()).isEqualTo("classpath");
        }
    }

    @Test
    void testShouldLoadJenkinsFileFromFilesystem() throws IOException {
        FileUtils.writeStringToFile(jenkinsPath.toFile(), "filesystem", Charset.defaultCharset());
        try (InputStream inputStream = resourceLoaderService.getResource(jenkinsPath).getInputStream()) {
            String fileContent = IOUtils.toString(inputStream, Charset.defaultCharset());

            assertThat(fileContent.trim()).isEqualTo("filesystem");
        }
    }

    @Test
    void testShouldLoadJenkinsFileFromClasspathIfNotPresentInFileSystem() throws IOException {
        try (InputStream inputStream = resourceLoaderService.getResource(jenkinsPath).getInputStream()) {
            String fileContent = IOUtils.toString(inputStream, Charset.defaultCharset());

            assertThat(fileContent.trim()).isEqualTo("classpath");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "*.txt", "\\*.txt" })
    void testLoadMultipleResourcesFromFilesystem(final String pathPattern) throws IOException {
        final String content = "filesystem";
        setupJavaFiles(content);

        Resource[] resources = resourceLoaderService.getResources(jenkinsFilesystemPaths.get(0).getParent(), pathPattern);
        assertThat(resources).hasSize(2);

        for (final Resource resource : resources) {
            final String actualContent = Files.readString(resource.getFile().toPath());
            assertThat(actualContent).isEqualTo(content);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "*.txt", "\\*.txt" })
    void testLoadMultipleResourcesNonOverridable(final String pathPattern) throws IOException {
        final String content = "filesystem";

        final Path path1 = Path.of("templates", "java", "p1.txt");
        FileUtils.writeStringToFile(path1.toFile(), content, Charset.defaultCharset());
        final Path path2 = Path.of("templates", "java", "p2.txt");
        FileUtils.writeStringToFile(path2.toFile(), content, Charset.defaultCharset());

        Resource[] resources = resourceLoaderService.getResources(Path.of("templates", "java"), pathPattern);
        assertThat(resources).hasSize(1);

        final String actualContent = Files.readString(resources[0].getFile().toPath());
        assertThat(actualContent.trim()).isEqualTo("classpath");
    }

    @Test
    void testLoadNonExistingResources() {
        Resource[] resources = resourceLoaderService.getResources(Path.of("non", "existing"), "*");
        assertThat(resources).isNotNull().isEmpty();
    }

    private void setupJavaFiles(final String content) throws IOException {
        for (Path javaFilesystemPath : jenkinsFilesystemPaths) {
            FileUtils.writeStringToFile(javaFilesystemPath.toFile(), content, Charset.defaultCharset());
        }
    }

    @Test
    void testGetResourceFilePathFromJar() throws IOException, URISyntaxException {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        URL resourceUrl = new URL("jar:file:/example.jar!/path/to/resource.txt");

        // Mock the getResource() method.
        doReturn(true).when(resource).exists();
        doReturn(resourceUrl).when(resource).getURL();
        doReturn(mock(InputStream.class)).when(resource).getInputStream();

        // ResourcePatternResolver resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
        doReturn(resource).when(resourceLoader).getResource(anyString());

        // Instantiate the class under test and invoke the method.
        ResourceLoaderService resourceLoaderService = new ResourceLoaderService(resourceLoader);
        Path path = Paths.get("path", "to", "resource.txt");
        Path resourceFilePath = resourceLoaderService.getResourceFilePath(path);

        // Verify the temporary file was created.
        assertThat(Files.exists(resourceFilePath)).isTrue();

        // Clean up the temporary file.
        Files.deleteIfExists(resourceFilePath);
    }
}
