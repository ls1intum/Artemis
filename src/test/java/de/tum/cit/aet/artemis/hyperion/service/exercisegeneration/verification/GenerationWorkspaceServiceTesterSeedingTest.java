package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Deterministic proof of the DECORRELATION guarantee: {@link GenerationWorkspaceService#seedTesterWorkspace} never seeds the reference {@code solution/} or the worked-sample
 * {@code reference/} into the independent examiner's container (decorrelation by absence), and {@link GenerationWorkspaceService#stripSampleTestSources} drops the co-authored
 * sample
 * test sources while keeping the harness. No Docker, no git, no model.
 */
class GenerationWorkspaceServiceTesterSeedingTest {

    private static GenerationWorkspaceService newWorkspaceService() {
        BuildPhasesTemplateService phases = mock(BuildPhasesTemplateService.class);
        when(phases.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of());
        SandboxBuildCommandService buildCommandService = new SandboxBuildCommandService(Optional.of(phases), Optional.of(new BuildScriptProviderService()));
        return new GenerationWorkspaceService(mock(GitService.class), mock(ProgrammingLanguageConfiguration.class), buildCommandService, mock(ResourceLoaderService.class));
    }

    @Test
    void seedTesterWorkspace_neverSeedsSolutionOrReference() {
        // A bare exercise has no repository URIs, so no repo working tree is checked out; the seeded tar must still carry the statement + verify.sh and provably NO
        // solution/reference.
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProblemStatement("# LRU cache\nEvicts the least-recently-used entry.");
        CapturingSandbox sandbox = new CapturingSandbox();

        newWorkspaceService().seedTesterWorkspace(sandbox, "s", exercise);

        assertThat(sandbox.seededEntryNames).contains("problem-statement.md", "verify.sh");
        assertThat(sandbox.seededEntryNames).as("the reference SOLUTION must never be seeded into the examiner's container").noneMatch(name -> name.startsWith("solution/"));
        assertThat(sandbox.seededEntryNames).as("the worked-sample reference must never be seeded into the examiner's container").noneMatch(name -> name.startsWith("reference/"));
    }

    @Test
    void stripSampleTestSources_dropsSampleTestsKeepsHarness() {
        Map<String, String> testsFiles = new java.util.LinkedHashMap<>();
        testsFiles.put("pom.xml", "<project/>");
        testsFiles.put(".gitignore", "target/");
        testsFiles.put("readme.md", "harness instructions");
        testsFiles.put("test/de/test/LRUCacheTest.java", "class LRUCacheTest {}");
        testsFiles.put("test/de/test/Helper.java", "class Helper {}");
        testsFiles.put("test/behavior/behavior_test.py", "def test_x(): pass");
        testsFiles.put("test/structural/__init__.py", "");

        Map<String, String> kept = GenerationWorkspaceService.stripSampleTestSources(testsFiles);

        assertThat(kept).containsKeys("pom.xml", ".gitignore", "readme.md", "test/de/test/Helper.java", "test/structural/__init__.py");
        assertThat(kept).as("the co-authored sample test sources are stripped so the examiner does not inherit their model").doesNotContainKeys("test/de/test/LRUCacheTest.java",
                "test/behavior/behavior_test.py");
    }

    /** Captures the tar entry names of the single {@code copyIn} the seed performs, so the test can assert what was (and was not) seeded. */
    private static final class CapturingSandbox implements InteractiveSandbox {

        private final List<String> seededEntryNames = new ArrayList<>();

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
            try (TarArchiveInputStream tar = new TarArchiveInputStream(tarArchive)) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        seededEntryNames.add(entry.getName());
                    }
                }
            }
            catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            return new SandboxExecResult(0, "", "", false);
        }

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }
}
