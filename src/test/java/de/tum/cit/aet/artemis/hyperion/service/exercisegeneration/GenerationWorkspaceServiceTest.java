package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
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
                mock(de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration.class), mock(SandboxBuildCommandService.class),
                mock(de.tum.cit.aet.artemis.core.service.ResourceLoaderService.class));
    }

    private static org.springframework.core.io.Resource fakeResource(String uri, byte[] content) throws Exception {
        var resource = mock(org.springframework.core.io.Resource.class);
        when(resource.getURI()).thenReturn(java.net.URI.create(uri));
        when(resource.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(content));
        return resource;
    }

    @Test
    void readReferenceSample_keysFilesUnderReferenceByLanguageRelativePath_andSkipsBinary() throws Exception {
        var loader = mock(de.tum.cit.aet.artemis.core.service.ResourceLoaderService.class);
        // Build the Resource mocks BEFORE stubbing the loader (nesting mock-stubbing inside another when(...) trips Mockito). The test/ area: a text sample (kept, keyed by its
        // path
        // under the language root) plus a binary asset (skipped so it is not packed as corrupt UTF-8), filesystem URI form. The solution/ area: a jar URI (proves the marker works
        // across filesystem and jar).
        org.springframework.core.io.Resource sampleTest = fakeResource("file:/x/src/main/resources/templates/java/test/testFiles/behavior/SortingExampleBehaviorTest.java",
                "class T {}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.springframework.core.io.Resource binaryAsset = fakeResource("file:/x/templates/java/test/wrapper.jar", new byte[] { 1, 0, 2 });
        org.springframework.core.io.Resource sampleSolution = fakeResource("jar:file:/a.war!/WEB-INF/classes/templates/java/solution/src/Foo.java",
                "class Foo {}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(loader.getFileResources(java.nio.file.Path.of("templates", "java", "test"))).thenReturn(new org.springframework.core.io.Resource[] { sampleTest, binaryAsset });
        when(loader.getFileResources(java.nio.file.Path.of("templates", "java", "solution"))).thenReturn(new org.springframework.core.io.Resource[] { sampleSolution });

        var service = new GenerationWorkspaceService(mock(de.tum.cit.aet.artemis.localvc.service.GitService.class),
                mock(de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration.class), mock(SandboxBuildCommandService.class), loader);
        var exercise = new de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise();
        exercise.setProgrammingLanguage(de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVA);

        Map<String, String> reference = service.readReferenceSample(exercise);

        assertThat(reference).containsEntry("reference/test/testFiles/behavior/SortingExampleBehaviorTest.java", "class T {}").containsEntry("reference/solution/src/Foo.java",
                "class Foo {}");
        assertThat(reference).as("binary assets are skipped, not packed as corrupt UTF-8").doesNotContainKey("reference/test/wrapper.jar");
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

    // --- Turn-0 workspace layout probe (Fix #2) ------------------------------------------------------------------------------------------------------------------------------

    @Test
    void probeWorkspaceLayout_listsTheSeededReposAndHeadsManifests_languageAgnostic() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        // The probe is a single `sh -c` invocation; capture the script and return a canned rendering. The script itself must list all three repo dirs and discover manifests by a
        // broad, language-agnostic name set (never hard-coding one toolchain).
        String canned = "--- ls -R solution template tests ---\nsolution:\nsrc\n\n--- head -40 solution/pom.xml ---\n<project/>";
        ArgumentCaptor<String[]> commandCaptor = ArgumentCaptor.forClass(String[].class);
        when(sandbox.exec(eq("s"), any(Duration.class), commandCaptor.capture())).thenReturn(new SandboxExecResult(0, canned, "", false));

        String layout = newService().probeWorkspaceLayout(sandbox, "s");

        assertThat(layout).isEqualTo(canned);
        String[] command = commandCaptor.getValue();
        assertThat(command[0]).isEqualTo("sh");
        assertThat(command[1]).isEqualTo("-c");
        String script = command[2];
        assertThat(script).as("runs through a shell").startsWith("cd /workspace");
        assertThat(script).as("lists all three seeded repository directories").contains("ls -R solution template tests");
        assertThat(script).as("discovers manifests across languages, not one hard-coded toolchain").contains("-name pom.xml").contains("-name Cargo.toml")
                .contains("-name package.json").contains("-name go.mod").contains("-name '*.cabal'").contains("-name dune-project").contains("-name Makefile")
                .contains("-name pyproject.toml");
        assertThat(script).as("heads whatever manifests exist").contains("head -40");
    }

    @Test
    void probeWorkspaceLayout_returnsEmptyOnEmptyWorkspace() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        // An empty workspace (or an empty repo) yields empty probe output; the probe must return an empty string so the caller leaves the prompt unchanged.
        when(sandbox.exec(eq("s"), any(Duration.class), any(String[].class))).thenReturn(new SandboxExecResult(0, "", "", false));

        assertThat(newService().probeWorkspaceLayout(sandbox, "s")).isEmpty();
    }

    @Test
    void probeWorkspaceLayout_returnsEmptyOnTimeoutOrError() {
        InteractiveSandbox timedOut = mock(InteractiveSandbox.class);
        when(timedOut.exec(eq("s"), any(Duration.class), any(String[].class))).thenReturn(new SandboxExecResult(-1, "partial", "", true));
        assertThat(newService().probeWorkspaceLayout(timedOut, "s")).as("a timed-out probe contributes nothing").isEmpty();

        InteractiveSandbox threw = mock(InteractiveSandbox.class);
        when(threw.exec(eq("s"), any(Duration.class), any(String[].class))).thenThrow(new RuntimeException("exec failed"));
        assertThat(newService().probeWorkspaceLayout(threw, "s")).as("a thrown probe is swallowed").isEmpty();
    }

    @Test
    void probeWorkspaceLayout_truncatesAnOversizedLayoutWithANotice() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        String huge = "x".repeat(10_000);
        when(sandbox.exec(eq("s"), any(Duration.class), any(String[].class))).thenReturn(new SandboxExecResult(0, huge, "", false));

        String layout = newService().probeWorkspaceLayout(sandbox, "s");

        assertThat(layout.length()).isLessThan(huge.length());
        assertThat(layout).endsWith("`ls -R` if you need more]");
    }
}
