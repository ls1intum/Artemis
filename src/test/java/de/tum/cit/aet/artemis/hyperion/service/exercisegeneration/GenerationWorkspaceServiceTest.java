package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Unit tests for {@link GenerationWorkspaceService#stripRedundantGitkeeps(Map)} — the deterministic cleanup that prevents a seeded {@code .gitkeep} from shipping in a source
 * directory the agent has populated (a recurring hygiene defect surfaced by per-language exercise reviews) — and for the read-back extraction-failed signal that lets the verifier
 * fail closed on a genuine read-back error while staying fail-open on a genuinely empty repository.
 */
class GenerationWorkspaceServiceTest {

    private static GenerationWorkspaceService newService() {
        return new GenerationWorkspaceService(mock(de.tum.cit.aet.artemis.localvc.service.GitService.class),
                mock(de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration.class), mock(SandboxBuildCommandService.class));
    }

    @Test
    void extractRepository_flagsExtractionFailed_whenCopyOutThrows() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut(anyString(), any())).thenThrow(new RuntimeException("docker copy failed"));

        GenerationWorkspaceService.RepositoryExtraction extraction = newService().extractRepository(sandbox, "s", RepositoryType.TESTS);

        // A thrown read-back is an ERROR, not a genuinely empty repo: the flag distinguishes the two so the verifier can fail CLOSED.
        assertThat(extraction.extractionFailed()).isTrue();
        assertThat(extraction.files()).isEmpty();
    }

    @Test
    void extractRepository_doesNotFlagFailure_whenCopyOutReturnsAGenuinelyEmptyRepo() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        // An empty tar (the repo really is empty) reads back as an empty map WITHOUT an exception, so it must NOT be flagged as failed (stays fail-open).
        TarArchiveInputStream emptyTar = new TarArchiveInputStream(InputStream.nullInputStream());
        when(sandbox.copyOut(eq("s"), any())).thenReturn(emptyTar);

        GenerationWorkspaceService.RepositoryExtraction extraction = newService().extractRepository(sandbox, "s", RepositoryType.TEMPLATE);

        assertThat(extraction.extractionFailed()).isFalse();
        assertThat(extraction.files()).isEmpty();
    }

    @Test
    void stripsGitkeepFromADirectoryThatNowHasARealSource() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/.gitkeep", "");
        files.put("src/stack.rb", "class Stack; end");
        files.put("Gemfile", "source 'x'");

        Map<String, String> cleaned = GenerationWorkspaceService.stripRedundantGitkeeps(files);

        assertThat(cleaned).containsOnlyKeys("src/stack.rb", "Gemfile");
    }

    @Test
    void keepsGitkeepInAStillEmptyDirectorySoTheLayoutSurvives() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("include/.gitkeep", "");
        files.put("src/stack.cpp", "// impl");

        Map<String, String> cleaned = GenerationWorkspaceService.stripRedundantGitkeeps(files);

        // include/ has no real file, so its .gitkeep is kept; src/ has a real file, so it had no .gitkeep to drop.
        assertThat(cleaned).containsOnlyKeys("include/.gitkeep", "src/stack.cpp");
    }

    @Test
    void keepsARootGitkeepWhenTheRepositoryIsOtherwiseEmpty() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(".gitkeep", "");

        assertThat(GenerationWorkspaceService.stripRedundantGitkeeps(files)).containsOnlyKeys(".gitkeep");
    }

    @Test
    void dropsARootGitkeepOnceTheRepositoryRootHasARealFile() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(".gitkeep", "");
        files.put("exercise.c", "int main(){}");

        assertThat(GenerationWorkspaceService.stripRedundantGitkeeps(files)).containsOnlyKeys("exercise.c");
    }
}
