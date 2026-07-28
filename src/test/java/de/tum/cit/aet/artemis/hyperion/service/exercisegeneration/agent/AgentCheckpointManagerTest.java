package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class AgentCheckpointManagerTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void replayRestoresTheCommittedPostTurnWithoutExecutingTheTurnAgain() throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getTitle()).thenReturn("Checkpoint exercise");
        ApprovedSpecRegistry approvedSpecs = new ApprovedSpecRegistry();
        InMemorySandbox recordedSandbox = new InMemorySandbox(Map.of("/workspace", Map.of("SPEC.md", "before".getBytes())));
        SandboxAgentTools recordedTools = new SandboxAgentTools(recordedSandbox, "recorded");
        AgentCheckpointManager recorder = new AgentCheckpointManager(mapper, tempDirectory.toString(), "", 0, true, "");
        recorder.beginRun("job-record", exercise, recordedTools, approvedSpecs);
        List<Message> prompt = List.of(new SystemMessage("system"), new UserMessage("author"));
        AgentCheckpointManager.LoopCursor beforeCursor = new AgentCheckpointManager.LoopCursor("", 0, 0, 0);
        AgentCheckpointManager.TurnHandle handle = recorder.beforeTurn(1, 5, "provider-v1", "tools-v1", prompt, beforeCursor);

        recordedSandbox.put("/workspace", "SPEC.md", "after");
        List<Message> afterConversation = List.of(new SystemMessage("system"), new UserMessage("author"), new AssistantMessage("done"));
        recorder.finishTurn(handle, afterConversation, new AgentCheckpointManager.LoopCursor("done", 0, 12, 2), AgentLoopResult.Status.COMPLETED);
        recorder.endRun();

        Path recordedRun = Files.list(tempDirectory).filter(Files::isDirectory).findFirst().orElseThrow();
        InMemorySandbox replaySandbox = new InMemorySandbox(Map.of("/workspace", Map.of("SPEC.md", "before".getBytes())));
        SandboxAgentTools replayTools = new SandboxAgentTools(replaySandbox, "replay");
        AgentCheckpointManager replayer = new AgentCheckpointManager(mapper, "", recordedRun.toString(), 0, true, "");
        replayer.beginRun("job-replay", exercise, replayTools, new ApprovedSpecRegistry());

        AgentCheckpointManager.TurnHandle replay = replayer.beforeTurn(1, 5, "", "tools-v1", prompt, beforeCursor);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.replayedTerminalStatus()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(AgentCheckpointMessageCodec.decode(replay.replayedAfter().conversation())).isEqualTo(afterConversation);
        assertThat(replaySandbox.text("/workspace", "SPEC.md")).isEqualTo("after");
        replayer.endRun();

        Files.writeString(recordedRun.resolve("calls/000001.json"), "\n", java.nio.file.StandardOpenOption.APPEND);
        AgentCheckpointManager tampered = new AgentCheckpointManager(mapper, "", recordedRun.toString(), 0, true, "");
        tampered.beginRun("job-tampered", exercise, new SandboxAgentTools(replaySandbox, "tampered"), new ApprovedSpecRegistry());
        assertThatThrownBy(() -> tampered.beforeTurn(1, 5, "", "tools-v1", prompt, beforeCursor)).isInstanceOf(IllegalStateException.class).hasMessageContaining("integrity check");
        tampered.endRun();
    }

    @Test
    void replayTreatsAnAbsentLegacyRootLikeAnEmptyDirectory() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(43L);
        when(exercise.getTitle()).thenReturn("Legacy checkpoint");
        InMemorySandbox sandbox = new InMemorySandbox(Map.of("/workspace", Map.of("SPEC.md", "spec".getBytes())));
        AgentCheckpointManager recorder = new AgentCheckpointManager(mapper, tempDirectory.toString(), "", 0, true, "");
        recorder.beginRun("legacy-record", exercise, new SandboxAgentTools(sandbox, "record"), new ApprovedSpecRegistry());
        List<Message> prompt = List.of(new SystemMessage("system"), new UserMessage("author"));
        AgentCheckpointManager.LoopCursor cursor = new AgentCheckpointManager.LoopCursor("", 0, 0, 0);
        AgentCheckpointManager.TurnHandle handle = recorder.beforeTurn(1, 1, "provider", "tools", prompt, cursor);
        recorder.finishTurn(handle, prompt, cursor, AgentLoopResult.Status.COMPLETED);
        recorder.endRun();

        Path run = Files.list(tempDirectory).filter(Files::isDirectory).findFirst().orElseThrow();
        Path call = run.resolve("calls/000001.json");
        ObjectNode record = (ObjectNode) mapper.readTree(call.toFile());
        ((ObjectNode) record.path("before").path("roots")).remove("/tmp/hyperion");
        ((ObjectNode) record.path("after").path("roots")).remove("/tmp/hyperion");
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(record);
        Files.write(call, bytes);
        Files.writeString(call.resolveSibling("000001.json.sha256"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "\n");

        AgentCheckpointManager replayer = new AgentCheckpointManager(mapper, "", run.toString(), 0, true, "");
        replayer.beginRun("legacy-replay", exercise, new SandboxAgentTools(sandbox, "replay"), new ApprovedSpecRegistry());
        assertThat(replayer.beforeTurn(1, 1, "", "tools", prompt, cursor).replayed()).isTrue();
        replayer.endRun();
    }

    @Test
    void agentLoopReplayDoesNotCallTheProvider() throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(7L);
        when(exercise.getTitle()).thenReturn("Replay");
        ChatModel recordingModel = mock(ChatModel.class);
        when(recordingModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("recorded answer")))));
        InMemorySandbox recordingSandbox = new InMemorySandbox(Map.of("/workspace", Map.of("SPEC.md", "spec".getBytes())));
        SandboxAgentTools recordingTools = new SandboxAgentTools(recordingSandbox, "record");
        Path recordRoot = tempDirectory.resolve("loop");
        AgentCheckpointManager recorder = new AgentCheckpointManager(mapper, recordRoot.toString(), "", 0, true, "");
        AgentLoopRunner recordingRunner = new AgentLoopRunner(List.of(recordingModel), 128_000, Duration.ofMinutes(5), new TestProviderFailureCooldown(), recorder);
        recordingRunner.beginCheckpointRun("loop-record", exercise, recordingTools, new ApprovedSpecRegistry());

        AgentLoopResult recorded = recordingRunner.run("system", "author", recordingTools, 3, () -> false, null, null);
        recordingRunner.endCheckpointRun();
        Path recordedRun = Files.list(recordRoot).filter(Files::isDirectory).findFirst().orElseThrow();

        InMemorySandbox replaySandbox = new InMemorySandbox(Map.of("/workspace", Map.of("SPEC.md", "spec".getBytes())));
        SandboxAgentTools replayTools = new SandboxAgentTools(replaySandbox, "replay");
        Path replayRoot = tempDirectory.resolve("loop-replay");
        AgentCheckpointManager replayer = new AgentCheckpointManager(mapper, replayRoot.toString(), recordedRun.toString(), 0, true, "");
        AgentLoopRunner replayRunner = new AgentLoopRunner(List.of(), 128_000, Duration.ofMinutes(5), new TestProviderFailureCooldown(), replayer);
        replayRunner.beginCheckpointRun("loop-replay", exercise, replayTools, new ApprovedSpecRegistry());

        AgentLoopResult replayed = replayRunner.run("system", "author", replayTools, 3, () -> false, null, null);
        replayRunner.endCheckpointRun();

        assertThat(replayed).isEqualTo(recorded);

        Path selfContainedReplay = Files.list(replayRoot).filter(Files::isDirectory).findFirst().orElseThrow();
        AgentCheckpointManager secondReplayer = new AgentCheckpointManager(mapper, "", selfContainedReplay.toString(), 0, true, "");
        AgentLoopRunner secondReplayRunner = new AgentLoopRunner(List.of(), 128_000, Duration.ofMinutes(5), new TestProviderFailureCooldown(), secondReplayer);
        SandboxAgentTools secondReplayTools = new SandboxAgentTools(replaySandbox, "replay-again");
        secondReplayRunner.beginCheckpointRun("loop-replay-again", exercise, secondReplayTools, new ApprovedSpecRegistry());
        assertThat(secondReplayRunner.run("system", "author", secondReplayTools, 3, () -> false, null, null)).isEqualTo(recorded);
        secondReplayRunner.endCheckpointRun();
    }

    @Test
    void reviewerReplayReturnsTheRecordedVerdictWithoutCallingTheProvider() throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(9L);
        when(exercise.getTitle()).thenReturn("Review replay");
        Path recordRoot = tempDirectory.resolve("reviews");
        AgentCheckpointManager recorder = new AgentCheckpointManager(mapper, recordRoot.toString(), "", 0, true, "");
        recorder.beginRun("review-record", exercise, new SandboxAgentTools(new InMemorySandbox(Map.of()), "record"), new ApprovedSpecRegistry());

        assertThat(recorder.reviewerCall("system", "candidate", "model-v1", () -> "accepted")).isEqualTo("accepted");
        assertThatThrownBy(() -> recorder.reviewerCall("system", "other candidate", "model-v1", () -> {
            throw new IllegalStateException("provider unavailable");
        })).isInstanceOf(IllegalStateException.class).hasMessage("provider unavailable");
        assertThat(recorder.reviewerCall("system", "final candidate", "model-v1", () -> "final verdict")).isEqualTo("final verdict");
        recorder.endRun();
        Path source = Files.list(recordRoot).filter(Files::isDirectory).findFirst().orElseThrow();

        AtomicBoolean providerCalled = new AtomicBoolean();
        AgentCheckpointManager replayer = new AgentCheckpointManager(mapper, "", source.toString(), 0, true, "");
        replayer.beginRun("review-replay", exercise, new SandboxAgentTools(new InMemorySandbox(Map.of()), "replay"), new ApprovedSpecRegistry());

        assertThat(replayer.reviewerCall("system", "candidate", "model-v1", () -> {
            providerCalled.set(true);
            return "different";
        })).isEqualTo("accepted");
        assertThat(providerCalled).isFalse();
        assertThatThrownBy(() -> replayer.reviewerCall("system", "other candidate", "model-v1", () -> {
            providerCalled.set(true);
            return "different";
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("provider unavailable");
        assertThat(replayer.reviewerCall("system", "final candidate", "model-v1", () -> {
            providerCalled.set(true);
            return "different";
        })).isEqualTo("final verdict");
        replayer.endRun();
        assertThat(providerCalled).isFalse();
    }

    @Test
    void reviewerForkReplaysTheAuthorPrefixAndRunsTheSelectedReviewAndSuffixLive() throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(11L);
        when(exercise.getTitle()).thenReturn("Review fork");
        Path recordRoot = tempDirectory.resolve("review-fork-source");
        InMemorySandbox sourceSandbox = new InMemorySandbox(Map.of("/workspace", Map.of("artifact.txt", "source".getBytes())));
        SandboxAgentTools sourceTools = new SandboxAgentTools(sourceSandbox, "source");
        AgentCheckpointManager recorder = new AgentCheckpointManager(mapper, recordRoot.toString(), "", 0, true, "");
        recorder.beginRun("review-fork-source", exercise, sourceTools, new ApprovedSpecRegistry());
        List<Message> firstPrompt = List.of(new SystemMessage("system"), new UserMessage("first"));
        List<Message> firstResult = List.of(new SystemMessage("system"), new UserMessage("first"), new AssistantMessage("first result"));
        AgentCheckpointManager.LoopCursor cursor = new AgentCheckpointManager.LoopCursor("", 0, 0, 0);
        AgentCheckpointManager.TurnHandle first = recorder.beforeTurn(1, 2, "provider-v1", "tools-v1", firstPrompt, cursor);
        recorder.finishTurn(first, firstResult, cursor, AgentLoopResult.Status.COMPLETED);
        assertThat(recorder.reviewerCall("old system", "old candidate", "reviewer-v1", () -> "old verdict")).isEqualTo("old verdict");
        AgentCheckpointManager.TurnHandle second = recorder.beforeTurn(2, 2, "provider-v1", "tools-v1", firstResult, cursor);
        recorder.finishTurn(second, firstResult, cursor, AgentLoopResult.Status.COMPLETED);
        recorder.endRun();
        Path source = Files.list(recordRoot).filter(Files::isDirectory).findFirst().orElseThrow();

        InMemorySandbox branchSandbox = new InMemorySandbox(Map.of("/workspace", Map.of("artifact.txt", "source".getBytes())));
        AgentCheckpointManager branch = new AgentCheckpointManager(mapper, tempDirectory.resolve("review-fork-branch").toString(), source.toString(), 0, 1, true, "");
        branch.beginRun("review-fork-branch", exercise, new SandboxAgentTools(branchSandbox, "branch"), new ApprovedSpecRegistry());

        assertThat(branch.beforeTurn(1, 2, "provider-v1", "tools-v1", firstPrompt, cursor).replayed()).isTrue();
        AtomicBoolean reviewerCalled = new AtomicBoolean();
        assertThat(branch.reviewerCall("new system", "new candidate", "reviewer-v1", () -> {
            reviewerCalled.set(true);
            return "new verdict";
        })).isEqualTo("new verdict");
        assertThat(reviewerCalled).isTrue();
        AgentCheckpointManager.TurnHandle liveSuffix = branch.beforeTurn(2, 2, "provider-v1", "tools-v1", firstResult, cursor);
        assertThat(liveSuffix.replayed()).isFalse();
        branch.finishTurn(liveSuffix, firstResult, cursor, AgentLoopResult.Status.COMPLETED);
        branch.endRun();
    }

    @Test
    void reviewerForkReplaysAnAuthorBranchWithItsInjectedInstruction() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(12L);
        when(exercise.getTitle()).thenReturn("Nested checkpoint fork");
        List<Message> prompt = List.of(new SystemMessage("system"), new UserMessage("author"));
        AgentCheckpointManager.LoopCursor cursor = new AgentCheckpointManager.LoopCursor("", 0, 0, 0);

        Path baseRoot = tempDirectory.resolve("nested-base");
        AgentCheckpointManager base = new AgentCheckpointManager(mapper, baseRoot.toString(), "", 0, true, "");
        base.beginRun("nested-base", exercise, new SandboxAgentTools(new InMemorySandbox(Map.of()), "base"), new ApprovedSpecRegistry());
        AgentCheckpointManager.TurnHandle baseTurn = base.beforeTurn(1, 1, "provider-v1", "tools-v1", prompt, cursor);
        base.finishTurn(baseTurn, List.of(new SystemMessage("system"), new UserMessage("author"), new AssistantMessage("base result")), cursor, AgentLoopResult.Status.COMPLETED);
        base.reviewerCall("review system", "base candidate", "reviewer-v1", () -> "base verdict");
        base.endRun();
        Path baseRun = Files.list(baseRoot).filter(Files::isDirectory).findFirst().orElseThrow();

        Path authorBranchRoot = tempDirectory.resolve("nested-author-branch");
        AgentCheckpointManager authorBranch = new AgentCheckpointManager(mapper, authorBranchRoot.toString(), baseRun.toString(), 1, true, "Prefer direct policy seams.");
        authorBranch.beginRun("nested-author-branch", exercise, new SandboxAgentTools(new InMemorySandbox(Map.of()), "author-branch"), new ApprovedSpecRegistry());
        AgentCheckpointManager.TurnHandle forkedTurn = authorBranch.beforeTurn(1, 1, "provider-v1", "tools-v1", prompt, cursor);
        List<Message> forkedConversation = new java.util.ArrayList<>(AgentCheckpointMessageCodec.decode(forkedTurn.before().conversation()));
        assertThat(forkedConversation.getLast().getText()).startsWith("CHECKPOINT FORK EXPERIMENT INSTRUCTION");
        forkedConversation.add(new AssistantMessage("branch result"));
        authorBranch.finishTurn(forkedTurn, forkedConversation, cursor, AgentLoopResult.Status.COMPLETED);
        authorBranch.reviewerCall("review system", "branch candidate", "reviewer-v1", () -> "branch verdict");
        authorBranch.endRun();
        Path authorBranchRun = Files.list(authorBranchRoot).filter(Files::isDirectory).findFirst().orElseThrow();
        Path legacyCall = authorBranchRun.resolve("calls/000001.json");
        ObjectNode legacyRecord = (ObjectNode) mapper.readTree(legacyCall.toFile());
        legacyRecord.remove("replayAnchor");
        byte[] legacyBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(legacyRecord);
        Files.write(legacyCall, legacyBytes);
        Files.writeString(legacyCall.resolveSibling("000001.json.sha256"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(legacyBytes)) + "\n");

        Path reviewerBranchRoot = tempDirectory.resolve("nested-reviewer-branch");
        AgentCheckpointManager reviewerBranch = new AgentCheckpointManager(mapper, reviewerBranchRoot.toString(), authorBranchRun.toString(), 0, 1, true, "");
        reviewerBranch.beginRun("nested-reviewer-branch", exercise, new SandboxAgentTools(new InMemorySandbox(Map.of()), "reviewer-branch"), new ApprovedSpecRegistry());
        AgentCheckpointManager.TurnHandle replayedTurn = reviewerBranch.beforeTurn(1, 1, "provider-v1", "tools-v1", prompt, cursor);
        assertThat(replayedTurn.replayed()).isTrue();
        assertThat(AgentCheckpointMessageCodec.decode(replayedTurn.replayedAfter().conversation()).stream().map(Message::getText))
                .anyMatch(text -> text != null && text.startsWith("CHECKPOINT FORK EXPERIMENT INSTRUCTION"));
        assertThat(reviewerBranch.reviewerCall("changed review system", "changed candidate", "reviewer-v1", () -> "new verdict")).isEqualTo("new verdict");
        reviewerBranch.endRun();
        Path reviewerBranchRun = Files.list(reviewerBranchRoot).filter(Files::isDirectory).findFirst().orElseThrow();
        assertThat(mapper.readTree(reviewerBranchRun.resolve("calls/000001.json").toFile()).path("replayAnchor").isObject()).isTrue();

        AgentCheckpointManager selfContainedReplay = new AgentCheckpointManager(mapper, "", reviewerBranchRun.toString(), 0, true, "");
        selfContainedReplay.beginRun("nested-full-replay", exercise, new SandboxAgentTools(new InMemorySandbox(Map.of()), "full-replay"), new ApprovedSpecRegistry());
        assertThat(selfContainedReplay.beforeTurn(1, 1, "", "tools-v1", prompt, cursor).replayed()).isTrue();
        assertThat(selfContainedReplay.reviewerCall("changed review system", "changed candidate", "reviewer-v1", () -> "unexpected")).isEqualTo("new verdict");
        selfContainedReplay.endRun();

        AgentCheckpointManager drifted = new AgentCheckpointManager(mapper, "", authorBranchRun.toString(), 0, 1, true, "");
        drifted.beginRun("nested-drift", exercise, new SandboxAgentTools(new InMemorySandbox(Map.of()), "drift"), new ApprovedSpecRegistry());
        List<Message> changedPrompt = List.of(new SystemMessage("changed system"), new UserMessage("author"));
        assertThatThrownBy(() -> drifted.beforeTurn(1, 1, "provider-v1", "tools-v1", changedPrompt, cursor)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Prompt drift");
        drifted.endRun();
    }

    @Test
    void reviewerForkConfigurationRejectsAmbiguousOrUnsupportedModes() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        assertThatThrownBy(() -> new AgentCheckpointManager(mapper, "", "source", 1, 1, true, "")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> new AgentCheckpointManager(mapper, "", "", 0, 1, true, "")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires checkpoint-replay-from");
        assertThatThrownBy(() -> new AgentCheckpointManager(mapper, "", "source", 0, 1, true, "change it")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only for an author call fork");
    }

    @Test
    void forkPreservesTheConsecutiveToolFailureCeiling() throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(8L);
        when(exercise.getTitle()).thenReturn("Failure cursor");
        ChatModel recordingModel = mock(ChatModel.class);
        ChatResponse unknownTool = toolCall("search");
        when(recordingModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(unknownTool, unknownTool, unknownTool, unknownTool,
                new ChatResponse(List.of(new Generation(new AssistantMessage("recorded completion")))));
        Path recordRoot = tempDirectory.resolve("failure-source");
        InMemorySandbox recordingSandbox = new InMemorySandbox(Map.of("/workspace", Map.of("SPEC.md", "spec".getBytes())));
        SandboxAgentTools recordingTools = new SandboxAgentTools(recordingSandbox, "record");
        AgentCheckpointManager recorder = new AgentCheckpointManager(mapper, recordRoot.toString(), "", 0, true, "");
        AgentLoopRunner recordingRunner = new AgentLoopRunner(List.of(recordingModel), 128_000, Duration.ofMinutes(5), new TestProviderFailureCooldown(), recorder);
        recordingRunner.beginCheckpointRun("failure-source", exercise, recordingTools, new ApprovedSpecRegistry());
        assertThat(recordingRunner.run("system", "author", recordingTools, 6, () -> false, null, null).status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        recordingRunner.endCheckpointRun();
        Path source = Files.list(recordRoot).filter(Files::isDirectory).findFirst().orElseThrow();

        ChatModel forkModel = mock(ChatModel.class);
        when(forkModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(unknownTool);
        InMemorySandbox forkSandbox = new InMemorySandbox(Map.of("/workspace", Map.of("SPEC.md", "spec".getBytes())));
        SandboxAgentTools forkTools = new SandboxAgentTools(forkSandbox, "fork");
        AgentCheckpointManager forkManager = new AgentCheckpointManager(mapper, tempDirectory.resolve("failure-fork").toString(), source.toString(), 5, true, "");
        AgentLoopRunner forkRunner = new AgentLoopRunner(List.of(forkModel), 128_000, Duration.ofMinutes(5), new TestProviderFailureCooldown(), forkManager);
        forkRunner.beginCheckpointRun("failure-fork", exercise, forkTools, new ApprovedSpecRegistry());

        AgentLoopResult forked = forkRunner.run("system", "author", forkTools, 6, () -> false, null, null);
        forkRunner.endCheckpointRun();

        assertThat(forked.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(forked.turns()).isEqualTo(5);
        verify(forkModel).call(org.mockito.ArgumentMatchers.any(Prompt.class));
    }

    @Test
    void forkAllowsTheLiveContinuationToDivergeAfterTheSelectedCall() throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(10L);
        when(exercise.getTitle()).thenReturn("Diverging fork");
        Path recordRoot = tempDirectory.resolve("divergence-source");
        InMemorySandbox sourceSandbox = new InMemorySandbox(Map.of());
        SandboxAgentTools sourceTools = new SandboxAgentTools(sourceSandbox, "source");
        ChatModel sourceModel = mock(ChatModel.class);
        when(sourceModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(toolCall("search"),
                new ChatResponse(List.of(new Generation(new AssistantMessage("source completion")))));
        AgentCheckpointManager recorder = new AgentCheckpointManager(mapper, recordRoot.toString(), "", 0, true, "");
        AgentLoopRunner sourceRunner = new AgentLoopRunner(List.of(sourceModel), 128_000, Duration.ofMinutes(5), new TestProviderFailureCooldown(), recorder);
        sourceRunner.beginCheckpointRun("divergence-source", exercise, sourceTools, new ApprovedSpecRegistry());
        assertThat(sourceRunner.run("system", "author", sourceTools, 3, () -> false, null, null).status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        sourceRunner.endCheckpointRun();
        Path source = Files.list(recordRoot).filter(Files::isDirectory).findFirst().orElseThrow();

        ChatModel branchModel = mock(ChatModel.class);
        when(branchModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(toolCall("browse"),
                new ChatResponse(List.of(new Generation(new AssistantMessage("branch completion")))));
        SandboxAgentTools branchTools = new SandboxAgentTools(new InMemorySandbox(Map.of()), "branch");
        AgentCheckpointManager branchManager = new AgentCheckpointManager(mapper, tempDirectory.resolve("divergence-branch").toString(), source.toString(), 1, true,
                "Do not call unavailable tools; finish with the available workspace evidence.");
        AgentLoopRunner branchRunner = new AgentLoopRunner(List.of(branchModel), 128_000, Duration.ofMinutes(5), new TestProviderFailureCooldown(), branchManager);
        branchRunner.beginCheckpointRun("divergence-branch", exercise, branchTools, new ApprovedSpecRegistry());

        AgentLoopResult branch = branchRunner.run("system", "changed author prompt", branchTools, 3, () -> false, null, null);
        branchRunner.endCheckpointRun();

        assertThat(branch.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(branch.finalMessage()).isEqualTo("branch completion");
        verify(branchModel, org.mockito.Mockito.times(2)).call(org.mockito.ArgumentMatchers.any(Prompt.class));
        verify(branchModel, org.mockito.Mockito.times(2)).call(org.mockito.ArgumentMatchers.<Prompt>argThat(prompt -> prompt.getInstructions().stream()
                .anyMatch(message -> message.getText() != null && message.getText().contains("CHECKPOINT FORK EXPERIMENT INSTRUCTION"))));
    }

    private static ChatResponse toolCall(String name) {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("call-1", "function", name, "{}");
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
    }

    private static final class InMemorySandbox implements InteractiveSandbox {

        private final Map<String, Map<String, byte[]>> roots = new LinkedHashMap<>();

        private InMemorySandbox(Map<String, Map<String, byte[]>> initial) {
            for (String root : List.of("/workspace", "/tmp/hyperion")) {
                roots.put(root, new LinkedHashMap<>());
            }
            initial.forEach((root, files) -> roots.put(root, new LinkedHashMap<>(files)));
        }

        void put(String root, String path, String content) {
            roots.computeIfAbsent(root, ignored -> new LinkedHashMap<>()).put(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String text(String root, String path) {
            return new String(roots.get(root).get(path), java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String createSession(SandboxSessionSpecDTO spec) {
            return "session";
        }

        @Override
        public SandboxExecResultDTO exec(String sessionId, Duration timeout, String... command) {
            String script = command[command.length - 1];
            if (script.startsWith("test -e '")) {
                String root = script.substring("test -e '".length(), script.length() - 1);
                return result(roots.containsKey(root) ? 0 : 1);
            }
            if (script.startsWith("mkdir -p '")) {
                String root = script.substring("mkdir -p '".length(), script.indexOf("' &&"));
                roots.put(root, new LinkedHashMap<>());
                return result(0);
            }
            return result(0);
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
            try (TarArchiveInputStream tar = new TarArchiveInputStream(tarArchive)) {
                WorkspaceArchive.BinaryArchiveContents contents = WorkspaceArchive.readBinaryTarContents(tar, "");
                roots.put(destinationPath, new LinkedHashMap<>(contents.files()));
            }
            catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            Map<String, byte[]> prefixed = new LinkedHashMap<>();
            String prefix = Path.of(path).getFileName().toString();
            roots.getOrDefault(path, Map.of()).forEach((name, content) -> prefixed.put(prefix + "/" + name, content));
            return new TarArchiveInputStream(WorkspaceArchive.buildFilesTarStream(Map.of(), prefixed, Set.of()));
        }

        @Override
        public void destroySession(String sessionId) {
        }

        private static SandboxExecResultDTO result(int exitCode) {
            return new SandboxExecResultDTO(exitCode, "", "", false);
        }
    }
}
