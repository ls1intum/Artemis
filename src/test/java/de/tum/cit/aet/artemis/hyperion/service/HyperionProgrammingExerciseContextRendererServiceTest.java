package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
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
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
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
    void getBuildEnvironmentContext_excludesSupportedSecretMaterial() throws IOException {
        String githubSentinel = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";
        FileUtils.writeStringToFile(tempDir.resolve("pom.xml").toFile(), "<project>safe</project>", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("gradle.properties").toFile(), githubSentinel, StandardCharsets.UTF_8);
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);

        String result = contextRendererService.getBuildEnvironmentContext(repository);

        assertThat(result).contains("pom.xml").doesNotContain("gradle.properties").doesNotContain(githubSentinel);
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

    @Test
    void getExistingSolutionCode_withSecretBearingSourceFile_dropsOnlyThatFile() throws Exception {
        String githubSentinel = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";
        Path solutionSrc = tempDir.resolve("src");
        Files.createDirectories(solutionSrc);
        FileUtils.writeStringToFile(solutionSrc.resolve("Config.java").toFile(), "class Config { String token = \"" + githubSentinel + "\"; }", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(solutionSrc.resolve("Library.java").toFile(), "class Library {}", StandardCharsets.UTF_8);

        ProgrammingExercise exerciseWithSolutionRepo = spy(exercise);
        when(exerciseWithSolutionRepo.getVcsSolutionRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        Repository solutionRepository = mock(Repository.class);
        when(solutionRepository.getLocalPath()).thenReturn(tempDir);
        when(gitService.getOrCheckoutRepository(any(), eq(true), eq("main"), eq(false))).thenReturn(solutionRepository);

        String result = contextRendererService.getExistingSolutionCode(exerciseWithSolutionRepo, gitService);

        // Production drops (never throws for) an unsafe file it encounters while scanning solution sources: the rest of the scan still completes.
        assertThat(result).contains("Library.java").doesNotContain("Config.java").doesNotContain(githubSentinel);
    }

    @Test
    void getExistingTestCode_withSecretBearingTestFile_dropsOnlyThatFile() throws Exception {
        String githubSentinel = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";
        FileUtils.writeStringToFile(tempDir.resolve("CredentialsTest.java").toFile(), "class CredentialsTest { String token = \"" + githubSentinel + "\"; }",
                StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("AaaTest.java").toFile(), "class AaaTest {}", StandardCharsets.UTF_8);

        ProgrammingExercise exerciseWithTestRepo = spy(exercise);
        when(exerciseWithTestRepo.getVcsTestRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        Repository testRepository = mock(Repository.class);
        when(testRepository.getLocalPath()).thenReturn(tempDir);
        when(gitService.getOrCheckoutRepository(any(), eq(true), eq("main"), eq(false))).thenReturn(testRepository);

        String result = contextRendererService.getExistingTestCode(exerciseWithTestRepo, gitService);

        // Production drops (never throws for) an unsafe file it encounters while scanning test sources: the rest of the scan still completes.
        assertThat(result).contains("AaaTest.java").doesNotContain("CredentialsTest.java").doesNotContain(githubSentinel);
    }

    @Test
    void getRepositoryStructure_omitsCredentialPathTreeEntries() throws IOException {
        Files.createDirectories(tempDir.resolve(".aws"));
        FileUtils.writeStringToFile(tempDir.resolve(".aws/credentials").toFile(), "ordinary", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("README.md").toFile(), "# Readme", StandardCharsets.UTF_8);
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);

        String result = contextRendererService.getRepositoryStructure(repository);

        // The credential-path suffix (".aws/credentials") is omitted from the tree; the ordinary sibling file still appears.
        assertThat(result).contains("README.md").doesNotContain("credentials");
    }

    @Test
    void renderContext_withSecretBearingProblemStatement_throwsBeforeAnyRepositoryAccess() {
        String githubSentinel = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";
        exercise.setProblemStatement("Implement login using token " + githubSentinel + " for CI.");
        TemplateProgrammingExerciseParticipation templateParticipation = mock(TemplateProgrammingExerciseParticipation.class);
        when(templateParticipation.getVcsRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        exercise.setTemplateParticipation(templateParticipation);

        assertThatExceptionOfType(HyperionSecretMaterialPolicy.SecretMaterialException.class).isThrownBy(() -> contextRendererService.renderContext(exercise))
                .withMessageContaining("GITHUB_TOKEN").withMessageNotContaining(githubSentinel);
        // The problem statement is checked before any repository is fetched; a repository that would otherwise be reachable (had the guard not fired) is never touched.
        verifyNoInteractions(repositoryService);
    }
}
