package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class GenerationWorkspaceServiceTest {

    @Test
    void prepareRepositoryForGeneration_removesExerciseArtifactsButKeepsBuildConfiguration() throws Exception {
        Path root = Files.createTempDirectory("hyperion-generation-seed");
        try {
            Files.createDirectories(root.resolve("src/de/test"));
            Files.writeString(root.resolve("src/de/test/BubbleSort.java"), "class BubbleSort {}");
            Files.writeString(root.resolve("pom.xml"), "<project />");

            GenerationWorkspaceService.prepareRepositoryForMode(root, RepositoryType.SOLUTION, GenerationMode.GENERATE);

            assertThat(root.resolve("src")).doesNotExist();
            assertThat(root.resolve("pom.xml")).hasContent("<project />");
        }
        finally {
            org.apache.commons.io.FileUtils.deleteDirectory(root.toFile());
        }
    }

    @Test
    void prepareTestRepositoryForGeneration_removesConventionalAndCategorizedTests() throws Exception {
        Path root = Files.createTempDirectory("hyperion-generation-tests-seed");
        try {
            for (String testRoot : Set.of("test", "behavior/test", "structural/test")) {
                Path test = root.resolve(testRoot).resolve("de/test/BubbleSortTest.java");
                Files.createDirectories(test.getParent());
                Files.writeString(test, "class BubbleSortTest {}");
            }
            Files.createDirectories(root.resolve("behavior"));
            Files.writeString(root.resolve("behavior/build.gradle"), "plugins {}");

            GenerationWorkspaceService.prepareRepositoryForMode(root, RepositoryType.TESTS, GenerationMode.GENERATE);

            assertThat(root.resolve("test")).doesNotExist();
            assertThat(root.resolve("behavior/test")).doesNotExist();
            assertThat(root.resolve("structural/test")).doesNotExist();
            assertThat(root.resolve("behavior/build.gradle")).hasContent("plugins {}");
        }
        finally {
            org.apache.commons.io.FileUtils.deleteDirectory(root.toFile());
        }
    }

    @Test
    void prepareRepositoryForAdaptation_preservesExistingArtifacts() throws Exception {
        Path root = Files.createTempDirectory("hyperion-adaptation-seed");
        try {
            Path source = root.resolve("src/de/test/Inventory.java");
            Files.createDirectories(source.getParent());
            Files.writeString(source, "class Inventory {}");

            GenerationWorkspaceService.prepareRepositoryForMode(root, RepositoryType.SOLUTION, GenerationMode.ADAPT);

            assertThat(source).hasContent("class Inventory {}");
        }
        finally {
            org.apache.commons.io.FileUtils.deleteDirectory(root.toFile());
        }
    }

    @Test
    void sessionSpec_disablesNetworkEgressByDefault() {
        ProgrammingLanguageConfiguration languageConfiguration = mock(ProgrammingLanguageConfiguration.class);
        when(languageConfiguration.getImage(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_GRADLE))).thenReturn("java-image");
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), languageConfiguration, mock(), mock(), tempFileUtilService());

        var spec = service.sessionSpec(exercise);

        assertThat(spec.image()).isEqualTo("java-image");
        assertThat(spec.runConfig().network()).isEqualTo("none");
    }

    @Test
    void seedWorkspace_failsClosedWhenARequiredRepositoryCannotBeCheckedOut() {
        GitService gitService = mock(GitService.class);
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getRepositoryURI(any(RepositoryType.class))).thenReturn(mock(LocalVCRepositoryUri.class));
        GenerationWorkspaceService service = new GenerationWorkspaceService(gitService, mock(), mock(), mock(), tempFileUtilService());

        assertThatThrownBy(() -> service.seedWorkspace(mock(InteractiveSandbox.class), "session", exercise, GenerationMode.GENERATE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEMPLATE repository");
    }

    @Test
    void extractRepository_rejectsResidueInsteadOfSilentlyPersistingDifferentFiles() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut("session", "/workspace/solution"))
                .thenReturn(tar(Map.of("solution/src/Main.java", "class Main {}", "solution/template/src/Leak.java", "class Leak {}")));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        GenerationWorkspaceService.RepositoryExtraction extraction = service.extractRepository(sandbox, "session", RepositoryType.SOLUTION,
                GenerationWorkspaceService.RepositorySeedMetadata.EMPTY);

        assertThat(extraction.extractionFailed()).isTrue();
        assertThat(extraction.files()).containsOnlyKeys("src/Main.java");
    }

    @Test
    void extractProblemStatement_failsClosedWhenTheFileIsMissing() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut("session", "/workspace/problem-statement.md")).thenReturn(tar(Map.of("other.md", "wrong file")));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        assertThatThrownBy(() -> service.extractProblemStatement(sandbox, "session")).isInstanceOf(IllegalStateException.class).hasMessageContaining("problem statement");
    }

    @Test
    void extractRepository_rejectsBinaryChangesThatPersistenceCannotRepresent() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut("session", "/workspace/solution")).thenReturn(tarBytes(Map.of("solution/tool.bin", new byte[] { 0, 4, 5 })));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        GenerationWorkspaceService.RepositoryExtraction extraction = service.extractRepository(sandbox, "session", RepositoryType.SOLUTION,
                new GenerationWorkspaceService.RepositorySeedMetadata(Map.of("tool.bin", WorkspaceArchive.sha256(new byte[] { 0, 1, 2 })), Set.of()));

        assertThat(extraction.extractionFailed()).isTrue();
    }

    @Test
    void extractRepository_rejectsExecutableModeChanges() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut("session", "/workspace/solution"))
                .thenReturn(tarBytes(Map.of("solution/run.sh", "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8)), Set.of("solution/run.sh")));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        GenerationWorkspaceService.RepositoryExtraction extraction = service.extractRepository(sandbox, "session", RepositoryType.SOLUTION,
                GenerationWorkspaceService.RepositorySeedMetadata.EMPTY);

        assertThat(extraction.extractionFailed()).isTrue();
    }

    private static TarArchiveInputStream tar(Map<String, String> files) {
        return tarBytes(files.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getBytes(StandardCharsets.UTF_8))));
    }

    private static TempFileUtilService tempFileUtilService() {
        return new TempFileUtilService(Path.of("build/tmp/hyperion-workspace-test"));
    }

    private static TarArchiveInputStream tarBytes(Map<String, byte[]> files) {
        return tarBytes(files, Set.of());
    }

    private static TarArchiveInputStream tarBytes(Map<String, byte[]> files, Set<String> executableFiles) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (TarArchiveOutputStream output = new TarArchiveOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    byte[] content = file.getValue();
                    TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                    entry.setSize(content.length);
                    entry.setMode(executableFiles.contains(file.getKey()) ? 0755 : 0644);
                    output.putArchiveEntry(entry);
                    output.write(content);
                    output.closeArchiveEntry();
                }
            }
            return new TarArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
