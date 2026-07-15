package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;

/**
 * Unit test for {@link FileSnapshotEmittingAgentTools}: the decorator must emit exactly one whole-file {@code FILE_SNAPSHOT} per successful write, with the correct repository
 * bucket, create-vs-edit action, and turn telemetry, while (a) never emitting on an unsafe path or a failed write, (b) coalescing an identical re-write, (c) reconstructing an
 * edit's whole content from its cached previous snapshot without a sandbox read, and (d) never letting a snapshot-sink failure disturb the agent run. A mocked delegate stands in
 * for the real sandbox tools so the test is fast and deterministic.
 */
class FileSnapshotEmittingAgentToolsTest {

    private static final String WRITE_OK = "Wrote 3 characters to path";

    private SandboxAgentTools delegate;

    private List<ExerciseGenerationFileSnapshotDTO> emitted;

    private FileSnapshotEmittingAgentTools tools;

    @BeforeEach
    void setUp() {
        delegate = mock(SandboxAgentTools.class);
        emitted = new ArrayList<>();
        Consumer<ExerciseGenerationFileSnapshotDTO> sink = emitted::add;
        tools = new FileSnapshotEmittingAgentTools(delegate, sink);
    }

    @Test
    void writeFile_onSuccess_emitsCreateSnapshotWithBucketTurnAndHash() {
        when(delegate.writeFile("solution/src/A.java", "abc")).thenReturn(WRITE_OK);
        tools.onTurn(4);

        String result = tools.writeFile("solution/src/A.java", "abc");

        assertThat(result).isEqualTo(WRITE_OK);
        assertThat(emitted).hasSize(1);
        ExerciseGenerationFileSnapshotDTO snapshot = emitted.getFirst();
        assertThat(snapshot.type()).isEqualTo(ExerciseGenerationFileSnapshotDTO.TYPE);
        assertThat(snapshot.path()).isEqualTo("solution/src/A.java");
        assertThat(snapshot.repo()).isEqualTo(ExerciseGenerationFileSnapshotDTO.REPOSITORY_SOLUTION);
        assertThat(snapshot.action()).isEqualTo(ExerciseGenerationFileSnapshotDTO.ACTION_CREATE);
        assertThat(snapshot.content()).isEqualTo("abc");
        assertThat(snapshot.bytes()).isEqualTo(3);
        assertThat(snapshot.turn()).isEqualTo(4);
    }

    @Test
    void writeFile_normalisesWorkspacePrefix_andSecondWriteToSamePathIsAnEdit() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        // First write of a /workspace-prefixed path: the snapshot path is normalised to workspace-relative and classified as a create.
        tools.writeFile("/workspace/tests/T.java", "one");
        // Second write of the same (normalised) path with different content is an edit, since the decorator already streamed a snapshot for it.
        tools.writeFile("tests/T.java", "two");

        assertThat(emitted).hasSize(2);
        assertThat(emitted.get(0).path()).isEqualTo("tests/T.java");
        assertThat(emitted.get(0).repo()).isEqualTo(ExerciseGenerationFileSnapshotDTO.REPOSITORY_TESTS);
        assertThat(emitted.get(0).action()).isEqualTo(ExerciseGenerationFileSnapshotDTO.ACTION_CREATE);
        assertThat(emitted.get(1).action()).isEqualTo(ExerciseGenerationFileSnapshotDTO.ACTION_EDIT);
        assertThat(emitted.get(1).content()).isEqualTo("two");
    }

    @Test
    void writeFile_coalescesAnIdenticalRewrite_soNoRedundantSnapshotIsStreamed() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        tools.writeFile("solution/A.java", "same");
        tools.writeFile("solution/A.java", "same");

        // Identical consecutive content for the same path streams once: the second write is a no-op change.
        assertThat(emitted).hasSize(1);
    }

    @Test
    void writeFile_onUnsafePath_delegatesButEmitsNothing() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        // A traversal path is rejected by the shared allowlist, so no snapshot can be classified/streamed even though the delegate ran.
        String result = tools.writeFile("../etc/passwd", "x");

        assertThat(result).isEqualTo(WRITE_OK);
        assertThat(emitted).isEmpty();
    }

    @Test
    void writeFile_onFailedDelegateResult_emitsNothing() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn("ERROR: could not write 'solution/A.java': disk full");

        String result = tools.writeFile("solution/A.java", "x");

        assertThat(result).startsWith("ERROR");
        assertThat(emitted).isEmpty();
    }

    @Test
    void writeFile_truncatesOversizedContentInTheSnapshot() {
        String big = "a".repeat(ExerciseGenerationFileSnapshotDTO.MAX_CONTENT_BYTES + 100);
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        tools.writeFile("solution/Big.java", big);

        assertThat(emitted).hasSize(1);
        assertThat(emitted.getFirst().truncated()).isTrue();
        assertThat(emitted.getFirst().bytes()).isEqualTo(big.length());
    }

    @Test
    void editFile_reconstructsWholeContentFromCachedSnapshot_withoutASandboxRead() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);
        when(delegate.editFile("solution/A.java", "world", "there")).thenReturn(WRITE_OK);
        // Seed the per-path cache with a known previous content via a write.
        tools.writeFile("solution/A.java", "hello world");

        String result = tools.editFile("solution/A.java", "world", "there");

        assertThat(result).isEqualTo(WRITE_OK);
        // The edit's whole-file snapshot is rebuilt from the cached previous content by applying the single replacement — no read-back through the delegate.
        assertThat(emitted).hasSize(2);
        assertThat(emitted.get(1).action()).isEqualTo(ExerciseGenerationFileSnapshotDTO.ACTION_EDIT);
        assertThat(emitted.get(1).content()).isEqualTo("hello there");
        verify(delegate, never()).readFile(anyString());
    }

    @Test
    void editFile_onCacheMiss_fallsBackToReadingTheFileBack() {
        when(delegate.editFile(anyString(), anyString(), anyString())).thenReturn(WRITE_OK);
        when(delegate.readFile("solution/A.java")).thenReturn("post-edit content");

        // No prior write cached this path, so the decorator reads the authoritative post-edit content back once to stream a faithful snapshot.
        tools.editFile("solution/A.java", "x", "y");

        assertThat(emitted).hasSize(1);
        assertThat(emitted.getFirst().content()).isEqualTo("post-edit content");
    }

    @Test
    void editFile_onCacheMissWithReadErrorSentinel_emitsNothing() {
        when(delegate.editFile(anyString(), anyString(), anyString())).thenReturn(WRITE_OK);
        when(delegate.readFile("solution/A.java")).thenReturn("ERROR: could not read 'solution/A.java'");

        // The read-back returned the error sentinel, which must never be cached or streamed as file content.
        tools.editFile("solution/A.java", "x", "y");

        assertThat(emitted).isEmpty();
    }

    @Test
    void deleteFile_onSuccess_replacesAnyRetainedContentWithAnEmptySnapshot() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);
        when(delegate.deleteFile("solution/A.java")).thenReturn("Deleted solution/A.java");
        tools.writeFile("solution/A.java", "class A {}");

        assertThat(tools.deleteFile("solution/A.java")).isEqualTo("Deleted solution/A.java");

        assertThat(emitted).hasSize(2);
        assertThat(emitted.getLast().action()).isEqualTo(ExerciseGenerationFileSnapshotDTO.ACTION_EDIT);
        assertThat(emitted.getLast().content()).isEmpty();
    }

    @Test
    void write_swallowsSinkFailure_soStreamingNeverDisturbsTheRun() {
        FileSnapshotEmittingAgentTools throwingSink = new FileSnapshotEmittingAgentTools(delegate, snapshot -> {
            throw new RuntimeException("sink exploded");
        });
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        // The write must return its result even when the snapshot sink throws: streaming is best-effort UX and cannot fail the agent's write.
        assertThatCode(() -> assertThat(throwingSink.writeFile("solution/A.java", "x")).isEqualTo(WRITE_OK)).doesNotThrowAnyException();
    }

    @Test
    void readBashVerifySubmit_arePureDelegationsThatEmitNothing() {
        when(delegate.readFile("solution/A.java")).thenReturn("content");
        when(delegate.bash("ls")).thenReturn("out");
        when(delegate.verify()).thenReturn("verified");
        when(delegate.submit("done")).thenReturn("submitted");

        assertThat(tools.readFile("solution/A.java")).isEqualTo("content");
        assertThat(tools.bash("ls")).isEqualTo("out");
        assertThat(tools.verify()).isEqualTo("verified");
        assertThat(tools.submit("done")).isEqualTo("submitted");
        assertThat(emitted).isEmpty();
    }
}
