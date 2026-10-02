package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Unit tests for the write-path policy of the programming toolset. Every file in a repository is editable —
 * a domain re-theme has to be able to rename the build's project name, which the previous src/ + test/ whitelist
 * made impossible — but paths may never escape the working copy or reach into git's own metadata.
 */
class ProgrammingVariantToolsPathTest {

    private static final String JOB_ID = "job-1";

    private GitService gitService;

    private RepositoryService repositoryService;

    private ProgrammingVariantTools tools;

    @BeforeEach
    void setUp() throws Exception {
        gitService = mock(GitService.class);
        repositoryService = mock(RepositoryService.class);
        ExerciseVariantJobService jobService = mock(ExerciseVariantJobService.class);
        when(jobService.isCancelRequested(JOB_ID)).thenReturn(false);

        // Mocked so the tools resolve a repository URI without a real LocalVC setup — this test is only about
        // which paths the write tools accept.
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getRepositoryURI(any())).thenReturn(mock(LocalVCRepositoryUri.class));
        Repository repository = mock(Repository.class);
        when(gitService.getOrCheckoutRepository(any(), anyBoolean(), anyString(), anyBoolean())).thenReturn(repository);
        // No file exists yet, so writeFile creates instead of replacing.
        when(gitService.getFileByName(any(), anyString())).thenReturn(Optional.empty());

        tools = new ProgrammingVariantTools(exercise, null, JOB_ID, jobService, gitService, repositoryService, null, null, "main", null, null, (exerciseArgument, jobArgument) -> {
        });
    }

    @Test
    void shouldAllowWritingBuildFilesAtTheRepositoryRoot() {
        String result = tools.writeFiles(List.of(new ProgrammingVariantTools.FileWrite("SOLUTION", "settings.gradle", "rootProject.name = 'cargo-bay'")));

        assertThat(result).contains("Entry 1 (SOLUTION:settings.gradle): written").contains("1 of 1 file(s) written");
    }

    @Test
    void shouldStillAllowWritingSourceFiles() {
        String result = tools.writeFiles(List.of(new ProgrammingVariantTools.FileWrite("SOLUTION", "src/de/tum/CargoBay.java", "class CargoBay {}")));

        assertThat(result).contains("Entry 1 (SOLUTION:src/de/tum/CargoBay.java): written").contains("1 of 1 file(s) written");
    }

    @Test
    void shouldAllowWritingDotfilesOutsideGitMetadata() {
        String result = tools.writeFiles(List.of(new ProgrammingVariantTools.FileWrite("TESTS", ".gitignore", "build/")));

        assertThat(result).contains("Entry 1 (TESTS:.gitignore): written").contains("1 of 1 file(s) written");
    }

    @Test
    void shouldRejectPathsReachingIntoGitMetadata() throws Exception {
        String result = tools.writeFiles(List.of(new ProgrammingVariantTools.FileWrite("SOLUTION", ".git/config", "[core]")));

        assertThat(result).contains("not a writable path").contains("0 of 1 file(s) written");
        verify(repositoryService, never()).createFile(any(), eq(".git/config"), any());
    }

    @Test
    void shouldRejectPathsEscapingTheWorkingCopy() throws Exception {
        assertThat(tools.writeFiles(List.of(new ProgrammingVariantTools.FileWrite("SOLUTION", "../../etc/passwd", "x")))).contains("0 of 1 file(s) written");
        assertThat(tools.writeFiles(List.of(new ProgrammingVariantTools.FileWrite("SOLUTION", "/etc/passwd", "x")))).contains("0 of 1 file(s) written");
        verify(repositoryService, never()).createFile(any(), anyString(), any());
    }

    @Test
    void shouldRejectDeletingGitMetadata() {
        assertThat(tools.deleteFiles(List.of(new ProgrammingVariantTools.FileDelete("SOLUTION", ".git/HEAD")))).contains("not a deletable path").contains("0 of 1 file(s) deleted");
    }

    @Test
    void shouldRejectEditingGitMetadata() {
        String result = tools.applyEdits(List.of(new ProgrammingVariantTools.BatchEdit("SOLUTION", ".git/config", "a", "b")));

        assertThat(result).contains("not an editable path").contains("0 of 1 edit(s) applied");
    }

    @Test
    void shouldRejectGitMetadataRegardlessOfCase() throws Exception {
        // Case-insensitive filesystems (macOS default, Windows) resolve ".GIT"/".Git" to the same directory as
        // ".git" — the guard must reject them the same way, not just the lowercase spelling.
        assertThat(tools.writeFiles(List.of(new ProgrammingVariantTools.FileWrite("SOLUTION", ".GIT/config", "[core]")))).contains("not a writable path")
                .contains("0 of 1 file(s) written");
        assertThat(tools.deleteFiles(List.of(new ProgrammingVariantTools.FileDelete("SOLUTION", ".Git/HEAD")))).contains("not a deletable path").contains("0 of 1 file(s) deleted");
        verify(repositoryService, never()).createFile(any(), eq(".GIT/config"), any());
    }

    @Test
    void shouldRejectANullReplaceInsteadOfWritingTheLiteralTextNull() throws Exception {
        when(repositoryService.getFile(any(), eq("settings.gradle"))).thenReturn("rootProject.name = 'cargo-bay'".getBytes(StandardCharsets.UTF_8));

        String result = tools.applyEdits(List.of(new ProgrammingVariantTools.BatchEdit("SOLUTION", "settings.gradle", "cargo-bay", null)));

        assertThat(result).contains("replace text must not be null");
        verify(repositoryService, never()).createFile(any(), eq("settings.gradle"), any());
    }
}
