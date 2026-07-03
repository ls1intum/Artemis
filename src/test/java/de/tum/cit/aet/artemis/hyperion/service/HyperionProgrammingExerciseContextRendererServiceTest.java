package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

class HyperionProgrammingExerciseContextRendererServiceTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private HyperionProgrammingLanguageContextFilterService languageFilter;

    @Mock
    private GitService gitService;

    @TempDir
    Path tempDir;

    @TempDir
    Path externalTempDir;

    private HyperionProgrammingExerciseContextRendererService contextRendererService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        contextRendererService = new HyperionProgrammingExerciseContextRendererService(repositoryService, languageFilter);

        exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Implement a sorting algorithm");
    }

    @Test
    void renderContext_withValidExercise_returnsFormattedContext() {
        String result = contextRendererService.renderContext(exercise);

        assertThat(result).isNotNull();
        assertThat(result).contains("Implement a sorting algorithm");
    }

    @Test
    void renderContext_withNullExercise_returnsEmptyString() {
        String result = contextRendererService.renderContext(null);

        assertThat(result).isEmpty();
    }

    @Test
    void renderContext_withNullProblemStatement_handlesGracefully() {
        exercise.setProblemStatement(null);

        String result = contextRendererService.renderContext(exercise);

        assertThat(result).isNotNull();
    }

    @Test
    void getExistingSolutionCode_withNullRepositoryUri_returnsWarningMessage() throws Exception {
        String result = contextRendererService.getExistingSolutionCode(exercise, gitService);

        assertThat(result).isEqualTo("No solution code available. Please refer to the problem statement.");
    }

    @Test
    void getExistingTestCode_withNullRepositoryUri_returnsMarker() {
        String result = contextRendererService.getExistingTestCode(exercise, gitService);

        assertThat(result).isEqualTo("No tests available yet.");
    }

    @Test
    void getExistingTestCode_ordersStructuralSpecBeforeTestSources() throws Exception {
        // AaaTest.java sorts before test.json alphabetically; the reader must still emit the structural spec first so it survives length capping.
        FileUtils.writeStringToFile(tempDir.resolve("AaaTest.java").toFile(), "class AaaTest {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("test.json").toFile(), "{\"structural\":true}", StandardCharsets.UTF_8);

        ProgrammingExercise exerciseWithTestRepo = spy(exercise);
        when(exerciseWithTestRepo.getVcsTestRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        Repository testRepository = mock(Repository.class);
        when(testRepository.getLocalPath()).thenReturn(tempDir);
        when(gitService.getOrCheckoutRepository(any(), eq(true), eq("main"), eq(false))).thenReturn(testRepository);

        String result = contextRendererService.getExistingTestCode(exerciseWithTestRepo, gitService);

        assertThat(result).contains("test.json").contains("AaaTest.java");
        assertThat(result.indexOf("test.json")).isLessThan(result.indexOf("AaaTest.java"));
    }

    @Test
    void renderContext_withNullProgrammingLanguage_handlesGracefully() {
        exercise.setProgrammingLanguage(null);

        String result = contextRendererService.renderContext(exercise);

        assertThat(result).isNotNull();
    }

    @Test
    void renderContext_withEmptyProblemStatement_handlesGracefully() {
        exercise.setProblemStatement("");

        String result = contextRendererService.renderContext(exercise);

        assertThat(result).isNotNull();
    }

    @Test
    void getBuildEnvironmentContext_withRelevantFiles_returnsFormattedContext() throws IOException {
        FileUtils.writeStringToFile(tempDir.resolve("pom.xml").toFile(), "<project>JUnit Jupiter</project>", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("requirements.txt").toFile(), "pytest==8.3.5", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("package.json").toFile(), "{\"name\":\"sample-exercise\"}", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("module"));
        FileUtils.writeStringToFile(tempDir.resolve("module/build.gradle").toFile(), "dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0' }",
                StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("target"));
        FileUtils.writeStringToFile(tempDir.resolve("target/ignored.gradle").toFile(), "ignored", StandardCharsets.UTF_8);

        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);

        String result = contextRendererService.getBuildEnvironmentContext(repository);

        assertThat(result).contains("Build Environment Files");
        assertThat(result).contains("pom.xml");
        assertThat(result).contains("requirements.txt");
        assertThat(result).contains("package.json");
        assertThat(result).contains("module/build.gradle");
        assertThat(result).contains("JUnit Jupiter");
        assertThat(result).contains("pytest==8.3.5");
        assertThat(result).contains("\"name\":\"sample-exercise\"");
        assertThat(result).doesNotContain("ignored.gradle");
    }

    @Test
    void getBuildEnvironmentContext_withSymlinkedBuildFile_skipsSymlinkTarget() throws IOException {
        Path externalBuildFile = externalTempDir.resolve("hyperion-external-build-file.xml");
        FileUtils.writeStringToFile(externalBuildFile.toFile(), "<project>outside</project>", StandardCharsets.UTF_8);
        Files.createSymbolicLink(tempDir.resolve("pom.xml"), externalBuildFile);

        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);

        String result = contextRendererService.getBuildEnvironmentContext(repository);

        assertThat(result).isEqualTo("No build environment files found.");
    }

    @Test
    void getBuildEnvironmentContext_withOversizedBuildFile_truncatesContent() throws IOException {
        String largeContent = "dependency=sample\n" + "x".repeat(4500);
        FileUtils.writeStringToFile(tempDir.resolve("requirements.txt").toFile(), largeContent, StandardCharsets.UTF_8);

        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);

        String result = contextRendererService.getBuildEnvironmentContext(repository);

        assertThat(result).contains("requirements.txt");
        assertThat(result).contains("... [truncated]");
        assertThat(result).doesNotContain("x".repeat(4100));
    }
}
