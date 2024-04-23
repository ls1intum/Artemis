package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;

class ResourceLoaderServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ResourceLoaderService resourceLoaderService;

    private final Path publicPath = Path.of("public", "public_file.txt");

    private final List<Path> publicFiles = List.of(Path.of("public", "p1.txt"), Path.of("public", "p2.txt"));

    private final Path jenkinsPath = Path.of("templates", "jenkins", "jenkins.txt");

    private final List<Path> jenkinsFilesystemPaths = List.of(Path.of("templates", "jenkins", "p1.txt"), Path.of("templates", "jenkins", "p2.txt"));

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(publicPath);
        for (final Path publicFile : publicFiles) {
            Files.deleteIfExists(publicFile);
        }
        Files.deleteIfExists(jenkinsPath);
    }

    @Test
    void testShouldNotAllowAbsolutePathsSingleResource() {
        final Path path = publicPath.toAbsolutePath();
        assertThatIllegalArgumentException().isThrownBy(() -> resourceLoaderService.getResource(path));
    }

    @Test
    void testShouldNotAllowAbsolutePathsMultipleResources() {
        final Path path = publicPath.toAbsolutePath();
        assertThatIllegalArgumentException().isThrownBy(() -> resourceLoaderService.getResources(path));
    }

    @Test
    void testShouldLoadPublicFileFromClasspath() throws IOException {
        FileUtils.writeStringToFile(publicPath.toFile(), "filesystem", Charset.defaultCharset());

        try (InputStream inputStream = resourceLoaderService.getResource(publicPath).getInputStream()) {
            assertThat(inputStream).hasContent("classpath");
        }
    }

    @Test
    void testShouldLoadJenkinsFileFromFilesystem() throws IOException {
        FileUtils.writeStringToFile(jenkinsPath.toFile(), "filesystem", Charset.defaultCharset());

        try (InputStream inputStream = resourceLoaderService.getResource(jenkinsPath).getInputStream()) {
            assertThat(inputStream).hasContent("filesystem");
        }
    }

    @Test
    void testShouldLoadJenkinsFileFromClasspathIfNotPresentInFileSystem() throws IOException {
        try (InputStream inputStream = resourceLoaderService.getResource(jenkinsPath).getInputStream()) {
            assertThat(inputStream).hasContent("classpath");
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
            assertThat(resource.getFile()).hasContent(content);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "*.txt", "\\*.txt" })
    void testLoadMultipleResourcesNonOverridable(final String pathPattern) throws IOException {
        final String content = "filesystem";

        for (final Path publicFile : publicFiles) {
            FileUtils.writeStringToFile(publicFile.toFile(), content, Charset.defaultCharset());
        }

        Resource[] resources = resourceLoaderService.getResources(Path.of("public"), pathPattern);
        assertThat(resources).hasSize(1);

        assertThat(resources[0].getFile()).hasContent("classpath");
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
        doReturn(InputStream.nullInputStream()).when(resource).getInputStream();

        doReturn(resource).when(resourceLoader).getResource(anyString());

        // Instantiate the class under test and invoke the method.
        ResourceLoaderService resourceLoaderService = new ResourceLoaderService(resourceLoader);
        Path path = Paths.get("path", "to", "resource.txt");
        Path resourceFilePath = resourceLoaderService.getResourceFilePath(path);

        // Verify the temporary file was created.
        assertThat(resourceFilePath).exists();

        // Clean up the temporary file.
        Files.deleteIfExists(resourceFilePath);
    }
}
