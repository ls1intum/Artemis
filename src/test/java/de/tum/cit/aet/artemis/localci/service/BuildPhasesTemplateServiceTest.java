package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Covers what happens to a build phases template that cannot be deserialized.
 * <p>
 * Jackson 3 raises an unchecked {@code JacksonException} where Jackson 2 raised a {@code JsonProcessingException}
 * extending {@link java.io.IOException}, so the recovery paths in {@link BuildPhasesTemplateService} - which all catch
 * {@code IOException} - would stop seeing a malformed template unless the service translates it. These tests pin that
 * translation, because a template reached through {@code artemis.template-path} is administrator-supplied and can be
 * malformed in production.
 */
@ExtendWith(MockitoExtension.class)
class BuildPhasesTemplateServiceTest {

    /** A mapping where the template format requires a list, which the deserializer cannot bind. */
    private static final String MALFORMED_TEMPLATE = "phases: not-a-list";

    private static final String TEMPLATE_FILE_NAME = "template.yaml";

    @Mock
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Mock
    private ResourceLoaderService resourceLoaderService;

    @Mock
    private BuildScriptProviderService buildScriptProviderService;

    private BuildPhasesTemplateService service;

    @BeforeEach
    void setUp() {
        service = new BuildPhasesTemplateService(programmingLanguageConfiguration, resourceLoaderService, buildScriptProviderService);
    }

    private void givenTheTemplateOnDiskIsMalformed() {
        when(buildScriptProviderService.buildTemplateName(any(), any(), any(), eq("yaml"))).thenReturn(TEMPLATE_FILE_NAME);
        when(resourceLoaderService.getResource(any(Path.class))).thenReturn(new ByteArrayResource(MALFORMED_TEMPLATE.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldReportAMalformedTemplateAsAnIOException() {
        givenTheTemplateOnDiskIsMalformed();

        // Not a JacksonException: the declared IOException is the contract every caller recovers from.
        assertThatIOException().isThrownBy(() -> service.getBuildPlanPhasesFor(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_MAVEN), false, false));
    }

    @Test
    void shouldTreatAMalformedTemplateAsAbsentForAnExercise() {
        givenTheTemplateOnDiskIsMalformed();

        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);
        exercise.setBuildConfig(new ProgrammingExerciseBuildConfig());

        // The exercise keeps its own configuration rather than the request failing.
        assertThat(service.getDefaultBuildPlanPhasesFor(exercise)).isNull();
    }

    @Test
    void shouldKeepBootingWhenATemplateIsMalformed() throws Exception {
        Resource malformedTemplate = mock(Resource.class);
        when(malformedTemplate.getFilename()).thenReturn(TEMPLATE_FILE_NAME);
        when(malformedTemplate.getURL()).thenReturn(URI.create("file:/artemis/templates/phases/java/" + TEMPLATE_FILE_NAME).toURL());
        when(malformedTemplate.getInputStream()).thenReturn(new ByteArrayInputStream(MALFORMED_TEMPLATE.getBytes(StandardCharsets.UTF_8)));
        when(resourceLoaderService.getFileResources(any(Path.class))).thenReturn(new Resource[] { malformedTemplate });

        // @PostConstruct: an escaping exception here would abort bean initialization instead of being logged.
        assertThatCode(service::cacheOnBoot).doesNotThrowAnyException();
    }
}
