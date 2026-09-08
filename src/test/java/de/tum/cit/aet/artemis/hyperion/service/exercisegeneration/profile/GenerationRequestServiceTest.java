package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

class GenerationRequestServiceTest {

    private static final String SAMPLE = "Implement the example sample task supplied by the programming exercise template.";

    private final ResourceLoaderService resources = mock();

    private final GenerationRequestService service = new GenerationRequestService(resources);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "short", "                                       " })
    void placeholdersAreNotAuthoritative(String statement) {
        var exercise = exercise();
        exercise.setProblemStatement(statement);
        assertThat(service.isAuthoritativeProblemStatement(exercise)).isFalse();
    }

    @Test
    void sampleReadmeIsWhitespaceInsensitiveAndCachedAndItsStreamIsClosed() throws Exception {
        Resource resource = mock();
        var input = org.mockito.Mockito.spy(new ByteArrayInputStream(SAMPLE.getBytes(StandardCharsets.UTF_8)));
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(input);
        when(resources.getResource(any(Path.class))).thenReturn(resource);
        var exercise = exercise();
        exercise.setProblemStatement(" \n" + SAMPLE.replace(" ", "\r\n  ") + "\n");
        assertThat(service.isAuthoritativeProblemStatement(exercise)).isFalse();
        assertThat(service.isAuthoritativeProblemStatement(exercise)).isFalse();
        verify(resources, times(1)).getResource(any(Path.class));
        verify(input).close();
    }

    @Test
    void unreadableProjectReadmeFallsBackToLanguageReadme() throws Exception {
        when(resources.getResource(any(Path.class))).thenThrow(new IllegalStateException("missing")).thenReturn(new ByteArrayResource(SAMPLE.getBytes(StandardCharsets.UTF_8)));
        var exercise = exercise();
        exercise.setProblemStatement(SAMPLE);
        assertThat(service.isAuthoritativeProblemStatement(exercise)).isFalse();
        verify(resources, times(2)).getResource(any(Path.class));
    }

    @Test
    void generationBriefOutranksExistingSpecAndAdaptationStaysScoped() {
        var exercise = exercise();
        exercise.setProblemStatement("An instructor-authored contract covering the original stack exercise and all operations.");
        assertThat(service.resolvePrompt(request(GenerationMode.GENERATE, "  change to queues  "), exercise)).contains("authoritative for this run", "change to queues");
        assertThat(service.resolvePrompt(request(GenerationMode.ADAPT, "fix push"), exercise)).contains("targeted revision", "fix push");
        assertThat(service.resolvePrompt(request(GenerationMode.GENERATE, null), exercise)).contains("authoritative specification");
        exercise.setProblemStatement(null);
        assertThat(service.resolvePrompt(request(GenerationMode.GENERATE, "  build a stack  "), exercise)).isEqualTo("build a stack");
        assertThat(service.resolvePrompt(request(GenerationMode.GENERATE, " "), exercise)).contains("template that compiles but fails");
        exercise.setProgrammingLanguage(null);
        exercise.setProblemStatement(SAMPLE);
        assertThat(service.isAuthoritativeProblemStatement(exercise)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ProjectType.class)
    void onlyCanonicalGradleIsQualified(ProjectType type) {
        var exercise = exercise();
        exercise.setProjectType(type);
        assertThat(service.isGenerationSupported(exercise)).isEqualTo(type == ProjectType.GRADLE_GRADLE);
    }

    @Test
    void unsupportedLanguageScaSequentialAndAuxiliaryConfigurationsStayClosed() {
        assertThat(service.supportedGenerationLanguages()).containsExactly(ProgrammingLanguage.JAVA);
        assertThat(service.isGenerationSupported(null)).isFalse();
        var exercise = exercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        assertThat(service.isGenerationSupported(exercise)).isFalse();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setStaticCodeAnalysisEnabled(true);
        assertThat(service.isGenerationSupported(exercise)).isFalse();
        exercise.setStaticCodeAnalysisEnabled(false);
        var config = new ProgrammingExerciseBuildConfig();
        config.setSequentialTestRuns(true);
        exercise.setBuildConfig(config);
        assertThat(service.isGenerationSupported(exercise)).isFalse();
        config.setSequentialTestRuns(false);
        assertThat(LanguageGenerationProfile.isSupported(exercise, true)).isFalse();
        assertThat(LanguageGenerationProfile.isSupported(exercise, false)).isTrue();
    }

    private static ProgrammingExercise exercise() {
        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.GRADLE_GRADLE);
        return exercise;
    }

    private static ExerciseGenerationRequestDTO request(GenerationMode mode, String prompt) {
        return new ExerciseGenerationRequestDTO(mode, prompt, null);
    }
}
