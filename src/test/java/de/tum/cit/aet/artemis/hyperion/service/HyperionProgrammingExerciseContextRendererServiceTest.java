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
    void renderContext_withoutLineNumbers_omitsTheGuttersButKeepsTheHeader() {
        String withGutters = contextRendererService.renderContext(exercise, true);
        String withoutGutters = contextRendererService.renderContext(exercise, false);

        assertThat(withGutters).contains("1 | Implement a sorting algorithm");
        assertThat(withoutGutters).contains("Implement a sorting algorithm").doesNotContain("1 | ");
        assertThat(withoutGutters).contains("problem_statement.md:").contains("-".repeat(80));
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

    @Test
    void repositoryStructureSortsDirectoriesBeforeFilesAndSkipsGeneratedArtifacts() throws Exception {
        Files.createDirectories(tempDir.resolve("z/sub"));
        Files.createDirectories(tempDir.resolve("a"));
        Files.createDirectories(tempDir.resolve("target"));
        FileUtils.writeStringToFile(tempDir.resolve("z/sub/B.java").toFile(), "class B {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("README.md").toFile(), "readme", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve(".DS_Store").toFile(), "ignored", StandardCharsets.UTF_8);
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);

        String result = contextRendererService.getRepositoryStructure(repository);

        assertThat(result).isEqualTo(tempDir.getFileName() + "/\n├── a/\n├── z/\n│   └── sub/\n│       └── B.java\n└── README.md\n");
    }

    @Test
    void repositoryStructureAndBuildContextHandleMissingAndNonDirectoryRoots() throws Exception {
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir.resolve("missing"));
        assertThat(contextRendererService.getRepositoryStructure(repository)).isEqualTo("Repository structure could not be determined.");
        assertThat(contextRendererService.getBuildEnvironmentContext(repository)).isEqualTo("No build environment files found.");
        FileUtils.writeStringToFile(tempDir.resolve("plain-file").toFile(), "text", StandardCharsets.UTF_8);
        when(repository.getLocalPath()).thenReturn(tempDir.resolve("plain-file"));
        assertThat(contextRendererService.getRepositoryStructure(repository)).isEqualTo("Repository structure could not be determined.");
        assertThat(contextRendererService.getBuildEnvironmentContext(repository)).isEqualTo("No build environment files found.");
        assertThat(contextRendererService.getBuildEnvironmentContext(null)).isEqualTo("No build environment files found.");
        when(repository.getLocalPath()).thenReturn(null);
        assertThat(contextRendererService.getBuildEnvironmentContext(repository)).isEqualTo("No build environment files found.");
        assertThat(contextRendererService.getRepositoryStructure(repository)).isEqualTo("Repository structure could not be determined due to an error.");
    }

    @Test
    void solutionCodeIncludesOnlyJavaUnderSourceRootAndReportsMissingCheckout() throws Exception {
        ProgrammingExercise withRepository = spy(exercise);
        when(withRepository.getVcsSolutionRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        assertThat(contextRendererService.getExistingSolutionCode(withRepository, gitService))
                .isEqualTo("Solution repository not accessible. Please refer to the problem statement.");
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);
        when(gitService.getOrCheckoutRepository(any(), eq(true), eq("main"), eq(false))).thenReturn(repository);
        assertThat(contextRendererService.getExistingSolutionCode(withRepository, gitService)).isEqualTo("No solution code found. Please refer to the problem statement.");
        Files.createDirectories(tempDir.resolve("src/main"));
        FileUtils.writeStringToFile(tempDir.resolve("src/main/Solver.java").toFile(), "class Solver {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("TopLevel.java").toFile(), "ignored", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("src/main/readme.txt").toFile(), "ignored", StandardCharsets.UTF_8);
        assertThat(contextRendererService.getExistingSolutionCode(withRepository, gitService)).isEqualTo("// File: src/main/Solver.java\nclass Solver {}\n\n");
        when(repository.getLocalPath()).thenReturn(tempDir.resolve("absent"));
        assertThat(contextRendererService.getExistingSolutionCode(withRepository, gitService)).isEqualTo("Failed to read solution code. Please refer to the problem statement.");
    }

    @Test
    void checkoutFailuresFailSolutionRequestsButTestContextFallsBack() throws Exception {
        ProgrammingExercise withRepository = spy(exercise);
        when(withRepository.getVcsSolutionRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        when(withRepository.getVcsTestRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        when(gitService.getOrCheckoutRepository(any(), eq(true), eq("main"), eq(false))).thenThrow(new IllegalStateException("checkout failed"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> contextRendererService.getExistingSolutionCode(withRepository, gitService))
                .isInstanceOf(de.tum.cit.aet.artemis.core.exception.NetworkingException.class).hasMessage("Failed to access solution repository")
                .hasRootCauseMessage("checkout failed");
        assertThat(contextRendererService.getExistingTestCode(withRepository, gitService)).isEqualTo("No tests available yet.");
    }

    @Test
    void testContextIgnoresSymlinksAndNonSourcesAndHandlesEmptyCheckout() throws Exception {
        ProgrammingExercise withRepository = spy(exercise);
        when(withRepository.getVcsTestRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        assertThat(contextRendererService.getExistingTestCode(withRepository, gitService)).isEqualTo("No tests available yet.");
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);
        when(gitService.getOrCheckoutRepository(any(), eq(true), eq("main"), eq(false))).thenReturn(repository);
        FileUtils.writeStringToFile(tempDir.resolve("notes.txt").toFile(), "ignored", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(externalTempDir.resolve("secret.java").toFile(), "secret", StandardCharsets.UTF_8);
        Files.createSymbolicLink(tempDir.resolve("Linked.java"), externalTempDir.resolve("secret.java"));
        assertThat(contextRendererService.getExistingTestCode(withRepository, gitService)).isEqualTo("No tests available yet.");
    }

    @Test
    void buildEnvironmentIgnoresHiddenDirectoriesAndIncludesHiddenToolchainFiles() throws Exception {
        Files.createDirectories(tempDir.resolve(".private"));
        FileUtils.writeStringToFile(tempDir.resolve(".private/pom.xml").toFile(), "secret", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve(".java-version").toFile(), "25", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(tempDir.resolve("unrelated.txt").toFile(), "ignore", StandardCharsets.UTF_8);
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(tempDir);
        assertThat(contextRendererService.getBuildEnvironmentContext(repository)).contains(".java-version", "1 | 25").doesNotContain("secret", "unrelated.txt");
    }

}
