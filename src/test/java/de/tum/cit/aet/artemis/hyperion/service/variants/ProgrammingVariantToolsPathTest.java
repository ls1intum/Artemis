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

        tools = new ProgrammingVariantTools(exercise, null, JOB_ID, jobService, gitService, repositoryService, null, null, null, null, null, null, "main");
    }

    @Test
    void shouldAllowWritingBuildFilesAtTheRepositoryRoot() {
        String result = tools.writeFile("SOLUTION", "settings.gradle", "rootProject.name = 'cargo-bay'");

        assertThat(result).startsWith("Wrote 'settings.gradle'");
    }

    @Test
    void shouldStillAllowWritingSourceFiles() {
        String result = tools.writeFile("SOLUTION", "src/de/tum/CargoBay.java", "class CargoBay {}");

        assertThat(result).startsWith("Wrote 'src/de/tum/CargoBay.java'");
    }

    @Test
    void shouldAllowWritingDotfilesOutsideGitMetadata() {
        String result = tools.writeFile("TESTS", ".gitignore", "build/");

        assertThat(result).startsWith("Wrote '.gitignore'");
    }

    @Test
    void shouldRejectPathsReachingIntoGitMetadata() throws Exception {
        String result = tools.writeFile("SOLUTION", ".git/config", "[core]");

        assertThat(result).startsWith("Error:").contains("not a writable path");
        verify(repositoryService, never()).createFile(any(), eq(".git/config"), any());
    }

    @Test
    void shouldRejectPathsEscapingTheWorkingCopy() throws Exception {
        assertThat(tools.writeFile("SOLUTION", "../../etc/passwd", "x")).startsWith("Error:");
        assertThat(tools.writeFile("SOLUTION", "/etc/passwd", "x")).startsWith("Error:");
        verify(repositoryService, never()).createFile(any(), anyString(), any());
    }

    @Test
    void shouldRejectDeletingGitMetadata() {
        assertThat(tools.deleteFile("SOLUTION", ".git/HEAD")).startsWith("Error:").contains("not a deletable path");
    }

    @Test
    void shouldRejectEditingGitMetadata() {
        assertThat(tools.applyEdit("SOLUTION", ".git/config", "a", "b")).startsWith("Error:").contains("not an editable path");
    }
}
