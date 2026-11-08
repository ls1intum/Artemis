package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress.FileChange;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.GenerationActivityTracker;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.FakeInteractiveSandbox;

class FileChangeEmittingAgentToolsTest {

    private static final String WRITE_OK = "Wrote 3 characters to path";

    private static final String EDIT_OK = "Replaced 1 occurrence in tests/T.java.";

    private SandboxAgentTools delegate;

    private List<FileChange> emitted;

    private FileChangeEmittingAgentTools tools;

    @BeforeEach
    void setUp() {
        delegate = mock(SandboxAgentTools.class);
        emitted = new ArrayList<>();
        tools = new FileChangeEmittingAgentTools(delegate, emitted::add);
    }

    @Test
    void streamingStagesCannotCompleteOnChatTextButUnstagedRepairCan() {
        SandboxAgentTools sandboxTools = new SandboxAgentTools(new FakeInteractiveSandbox(), "s");
        FileChangeEmittingAgentTools streamingTools = new FileChangeEmittingAgentTools(sandboxTools, emitted::add);
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Finished in chat")))));
        AgentLoopRunner runner = new AgentLoopRunner(List.of(model), 128_000, Duration.ZERO, ProviderFailureCooldown.disabled());

        for (GenerationStage stage : GenerationStage.values()) {
            sandboxTools.enterStage(stage);
            var result = runner.run("system", "brief", streamingTools, 2, () -> false, null, null);
            assertThat(result.status()).as("stage %s must explicitly submit", stage).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
            assertThat(result.turns()).isEqualTo(2);
        }
        sandboxTools.enterRepairScope(Set.of("tests"));
        var repair = runner.run("system", "repair", streamingTools, 2, () -> false, null, null);
        assertThat(repair.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(repair.turns()).isEqualTo(1);
        assertThat(emitted).isEmpty();
    }

    @Test
    void successfulWritesEmitLightweightWriteChanges() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);
        when(delegate.latestMutationContent()).thenReturn("first", "second");
        tools.onTurn(4);

        tools.writeFile("/workspace/solution/src/A.java", "first");
        tools.writeFile("solution/src/A.java", "second");

        assertThat(emitted).hasSize(2);
        assertThat(emitted.getFirst()).satisfies(update -> {
            var change = update;
            assertThat(change.path()).isEqualTo("solution/src/A.java");
            assertThat(change.action()).isEqualTo("write");
            assertThat(change.turn()).isEqualTo(4);
        });
        assertThat(emitted.getLast().action()).isEqualTo("write");
        assertThat(emitted).extracting(FileChange::content).containsExactly("first", "second");
    }

    @Test
    void successfulEditEmitsWithoutReadingFileContentBack() {
        when(delegate.editFile("tests/T.java", "old", "new")).thenReturn(EDIT_OK);
        when(delegate.latestMutationContent()).thenReturn("complete edited file");

        assertThat(tools.editFile("tests/T.java", "old", "new")).isEqualTo(EDIT_OK);

        assertThat(emitted).singleElement().satisfies(update -> {
            var change = update;
            assertThat(change.action()).isEqualTo("edit");
            assertThat(update.content()).isEqualTo("complete edited file");
        });
    }

    @Test
    void successfulDeleteEmitsDeleteAndRecreationIsWrite() {
        when(delegate.deleteFile("solution/A.java")).thenReturn("Deleted solution/A.java");
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);
        tools.writeFile("solution/A.java", "old");

        tools.deleteFile("solution/A.java");
        tools.writeFile("solution/A.java", "new");

        assertThat(emitted).extracting(update -> update.action()).containsExactly("write", "delete", "write");
    }

    @Test
    void unsafePathsAndFailedWritesEmitNothing() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK, "ERROR: disk full");

        tools.writeFile("../etc/passwd", "x");
        tools.writeFile("solution/A.java", "x");

        assertThat(emitted).isEmpty();
    }

    @Test
    void sinkFailureDoesNotFailTheAgentWrite() {
        FileChangeEmittingAgentTools throwingSink = new FileChangeEmittingAgentTools(delegate, change -> {
            throw new RuntimeException("sink exploded");
        });
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        assertThatCode(() -> assertThat(throwingSink.writeFile("solution/A.java", "x")).isEqualTo(WRITE_OK)).doesNotThrowAnyException();
    }

    @Test
    void readSearchBashVerifyAndSubmitRemainPureDelegations() {
        when(delegate.readFile("solution/A.java", null, null)).thenReturn("content");
        when(delegate.search("problem-statement.md", "empty")).thenReturn("3:empty");
        when(delegate.bash("ls")).thenReturn("out");
        when(delegate.verify()).thenReturn("verified");
        when(delegate.submit("done")).thenReturn("submitted");

        assertThat(tools.readFile("solution/A.java", null, null)).isEqualTo("content");
        assertThat(tools.search("problem-statement.md", "empty")).isEqualTo("3:empty");
        assertThat(tools.bash("ls")).isEqualTo("out");
        assertThat(tools.verify()).isEqualTo("verified");
        assertThat(tools.submit("done")).isEqualTo("submitted");
        assertThat(emitted).isEmpty();
    }

    @Test
    void consumeSubmitVetoDelegatesToTheWrappedTools() {
        when(delegate.consumeSubmitVeto()).thenReturn(true, false);

        assertThat(tools.consumeSubmitVeto()).isTrue();
        assertThat(tools.consumeSubmitVeto()).isFalse();
    }

    @Test
    void onlySuccessfulFileMutationsAreCountedIntoTheRunActivity() {
        // Counted here and nowhere else: this decorator is already the one place that knows a write, edit, or delete actually landed.
        GenerationActivityTracker tracker = new GenerationActivityTracker();
        FileChangeEmittingAgentTools countingTools = new FileChangeEmittingAgentTools(delegate, emitted::add, tracker);
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK, "ERROR: outside the workspace");
        when(delegate.editFile(anyString(), anyString(), anyString())).thenReturn(EDIT_OK);
        when(delegate.deleteFile(anyString())).thenReturn("ERROR: no such file");

        countingTools.writeFile("solution/src/A.java", "first");
        countingTools.writeFile("../escape.java", "second");
        countingTools.editFile("tests/T.java", "old", "new");
        countingTools.deleteFile("tests/Missing.java");

        assertThat(tracker.snapshot().filesWritten()).isEqualTo(2);
    }

    @Test
    void withoutATracker_theDecoratorStillStreamsFileChanges() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        tools.writeFile("solution/src/A.java", "first");

        assertThat(emitted).hasSize(1);
    }
}
