package de.tum.cit.aet.artemis.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

class TemplateFileResourceTest {

    private ResourceLoaderService loader;

    private TemplateFileResource controller;

    @BeforeEach
    void setUp() {
        loader = mock(ResourceLoaderService.class);
        controller = new TemplateFileResource(loader);
    }

    @Test
    void returnsProjectReadmeAndClosesStream() throws IOException {
        var resource = mock(Resource.class);
        var stream = spy(new ByteArrayInputStream("project readme".getBytes(StandardCharsets.UTF_8)));
        when(loader.getResource(Path.of("templates", "java", "plain_maven", "readme"))).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(stream);

        var response = controller.getTemplateFile(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_MAVEN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("project readme".getBytes(StandardCharsets.UTF_8));
        verify(stream).close();
    }

    @Test
    void fallsBackToLanguageReadmeWhenProjectReadmeIsMissing() throws IOException {
        var missing = mock(Resource.class);
        var fallback = mock(Resource.class);
        when(loader.getResource(Path.of("templates", "java", "plain_maven", "readme"))).thenReturn(missing);
        when(loader.getResource(Path.of("templates", "java", "readme"))).thenReturn(fallback);
        when(fallback.getInputStream()).thenReturn(new ByteArrayInputStream("fallback".getBytes(StandardCharsets.UTF_8)));

        var response = controller.getTemplateFile(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_MAVEN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("fallback".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void loadsLanguageReadmeWithoutProjectType() throws IOException {
        var resource = mock(Resource.class);
        when(loader.getResource(Path.of("templates", "python", "readme"))).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("python".getBytes(StandardCharsets.UTF_8)));

        var response = controller.getTemplateFile(ProgrammingLanguage.PYTHON, Optional.empty());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("python".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void returnsNotFoundWhenReadmeCannotBeRead() throws IOException {
        var resource = mock(Resource.class);
        when(loader.getResource(Path.of("templates", "python", "readme"))).thenReturn(resource);
        when(resource.getInputStream()).thenThrow(new IOException("missing readme"));

        var response = controller.getTemplateFile(ProgrammingLanguage.PYTHON, Optional.empty());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void returnsUtf8CodeOfConductAndClosesStream() throws IOException {
        var resource = mock(Resource.class);
        var stream = spy(new ByteArrayInputStream("Respect für alle".getBytes(StandardCharsets.UTF_8)));
        when(loader.getResource(Path.of("templates", "codeofconduct", "README.md"))).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(stream);

        assertThat(controller.getCourseCodeOfConduct().getBody()).isEqualTo("Respect für alle");
        verify(stream).close();
    }

    @Test
    void preservesCodeOfConductIoFailure() throws IOException {
        var resource = mock(Resource.class);
        when(loader.getResource(Path.of("templates", "codeofconduct", "README.md"))).thenReturn(resource);
        when(resource.getInputStream()).thenThrow(new IOException("missing conduct"));

        assertThatIOException().isThrownBy(() -> controller.getCourseCodeOfConduct()).withMessage("missing conduct");
    }
}
