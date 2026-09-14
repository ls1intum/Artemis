package de.tum.cit.aet.artemis.hyperionworker.generation.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief.Mode;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperionworker.generation.FakeInteractiveSandbox;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationInput;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationResources;
import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;

class GenerationWorkspaceServiceTest {

    private final GenerationResources resources = new GenerationResources();

    private final GenerationWorkspaceService service = new GenerationWorkspaceService(new SandboxBuildCommandService(resources), resources);

    private final GenerationInput exercise = new GenerationInput(1, "de.test", "Existing statement", false, Set.of());

    private final RecordingSandbox sandbox = new RecordingSandbox();

    @Test
    void generateClearsAuthoredRootsButPreservesHarnessBytesAndModes() {
        byte[] wrapper = { 0x50, 0x4b, 0, 1, (byte) 0xff };
        var snapshot = new WorkspaceSnapshot(List.of(text("solution/src/de/test/Old.java", "old solution", false), text("template/src/de/test/Old.java", "old template", false),
                text("tests/test/de/test/OldTest.java", "old test", false), text("tests/behavior/test/OldTest.java", "old behavior", false),
                text("tests/structural/test/OldTest.java", "old structural", false), text("tests/build.gradle", "canonical harness", false),
                text("tests/gradlew", "#!/bin/sh\nexec gradle \"$@\"", true), new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", wrapper, false)));

        var seed = service.seedWorkspace(sandbox, "session", exercise, Mode.GENERATE, snapshot, false);

        assertThat(seed.repositoryTextFiles().get(RepositoryRole.SOLUTION)).isEmpty();
        assertThat(seed.repositoryTextFiles().get(RepositoryRole.TEMPLATE)).isEmpty();
        assertThat(seed.testsSeedSnapshot()).containsOnlyKeys("build.gradle", "gradlew");
        assertThat(seed.repositoryBinaryFiles().get(RepositoryRole.TESTS).get("gradle/wrapper/gradle-wrapper.jar").content()).isEqualTo(wrapper);
        assertThat(seed.repositoryMetadata().get(RepositoryRole.TESTS).executableFiles()).containsExactly("gradlew");
        assertThat(sandbox.contents.binaryDigests()).containsEntry("tests/gradle/wrapper/gradle-wrapper.jar", WorkspaceArchive.sha256(wrapper));
        assertThat(sandbox.contents.executableFiles()).containsExactly("tests/gradlew");
        assertThat(sandbox.contents.textFiles()).containsEntry("tests/build.gradle", "canonical harness").containsEntry("problem-statement.md", "")
                .doesNotContainKeys("solution/src/de/test/Old.java", "template/src/de/test/Old.java", "tests/test/de/test/OldTest.java");
    }

    @Test
    void adaptPreservesExistingAuthoredFilesAndStatement() {
        var snapshot = new WorkspaceSnapshot(List.of(text("solution/src/de/test/Old.java", "solution", false), text("template/src/de/test/Old.java", "template", false),
                text("tests/test/de/test/OldTest.java", "test", false)));

        var seed = service.seedWorkspace(sandbox, "session", exercise, Mode.ADAPT, snapshot, true);

        assertThat(seed.testsSeedSnapshot()).containsEntry("test/de/test/OldTest.java", "test");
        assertThat(sandbox.contents.textFiles()).containsEntry("solution/src/de/test/Old.java", "solution").containsEntry("template/src/de/test/Old.java", "template")
                .containsEntry("tests/test/de/test/OldTest.java", "test").containsEntry("problem-statement.md", "Existing statement");
        assertThat(sandbox.contents.textFiles().keySet()).noneMatch(path -> path.startsWith("reference/"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void nonAuthoritativeStatementIsNotSeeded(Mode mode) {
        service.seedWorkspace(sandbox, "session", exercise, mode, new WorkspaceSnapshot(List.of()), false);

        assertThat(sandbox.contents.textFiles()).containsEntry("problem-statement.md", "");
    }

    @Test
    void generatePreservesAnAuthoritativeInstructorStatement() {
        service.seedWorkspace(sandbox, "session", exercise, Mode.GENERATE, new WorkspaceSnapshot(List.of()), true);

        assertThat(sandbox.contents.textFiles()).containsEntry("problem-statement.md", "Existing statement");
        assertThat(sandbox.contents.textFiles()).containsKeys("reference/problem-statement.md", "verify.sh");
    }

    @Test
    void missingStatementSeedsAnEmptyFile() {
        service.seedWorkspace(sandbox, "session", new GenerationInput(1, "de.test", null, false, Set.of()), Mode.ADAPT, new WorkspaceSnapshot(List.of()), true);

        assertThat(sandbox.contents.textFiles()).containsEntry("problem-statement.md", "");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void reMaterializationRestoresTheWholeCapturedCandidateWithoutLosingBinaryModesOrRootArtifacts(Mode mode) {
        byte[] binary = { 0, 42, (byte) 0xff };
        service.materializeRepositoryFiles(sandbox, "session", exercise, mode, Map.of(RepositoryRole.TESTS, Map.of("gradlew", "#!/bin/sh")),
                Map.of(RepositoryRole.TESTS, new GenerationWorkspaceService.RepositorySeedMetadata(Map.of("wrapper.jar", WorkspaceArchive.sha256(binary)), Set.of("gradlew"))),
                Map.of(RepositoryRole.TESTS, Map.of("wrapper.jar", new GenerationWorkspaceService.BinarySeedFile(binary))), "statement", "approved specification", "plan");

        assertThat(sandbox.contents.textFiles()).containsEntry("tests/gradlew", "#!/bin/sh").containsEntry("problem-statement.md", "statement")
                .containsEntry("SPEC.md", "approved specification").containsEntry("test-plan.json", "plan").containsKey("verify.sh");
        assertThat(sandbox.contents.binaryDigests()).containsEntry("tests/wrapper.jar", WorkspaceArchive.sha256(binary));
        assertThat(sandbox.contents.executableFiles()).containsExactly("tests/gradlew");
        assertThat(sandbox.contents.textFiles().containsKey("reference/problem-statement.md")).isEqualTo(mode == Mode.GENERATE);
    }

    private static WorkspaceFile text(String path, String content, boolean executable) {
        return new WorkspaceFile(path, content.getBytes(StandardCharsets.UTF_8), executable);
    }

    private static class RecordingSandbox extends FakeInteractiveSandbox {

        private WorkspaceArchive.ArchiveContents contents;

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream archive) {
            assertThat(destinationPath).isEqualTo("/workspace");
            try (var tar = new TarArchiveInputStream(archive)) {
                contents = WorkspaceArchive.readTarContents(tar, "");
            }
            catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }
}
