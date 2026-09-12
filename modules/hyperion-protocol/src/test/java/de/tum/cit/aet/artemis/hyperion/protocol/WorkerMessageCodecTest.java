package de.tum.cit.aet.artemis.hyperion.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class WorkerMessageCodecTest {

    private static final String IMAGE = "sha256:" + "a".repeat(64);

    private final WorkerMessageCodec codec = new WorkerMessageCodec();

    @ParameterizedTest
    @ValueSource(strings = { "standard", "" })
    @NullSource
    void commandRoundTripPreservesEmptyFilesBinaryWrappersAndDurations(String effortProfile) {
        var identity = identity();
        var seed = new WorkspaceSnapshot(List.of(new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", new byte[] { -1, 0, 42 }, false),
                new WorkspaceFile("template/src/.gitkeep", new byte[0], false), new WorkspaceFile("tests/gradlew", new byte[] { 35, 33 }, true)));
        var parameters = new GenerationParameters(effortProfile, 10, 100_000, Duration.ofMinutes(5), 128_000, null, null, null, null, null, true, "CONTINUOUS");
        var assignment = new GenerationAssignment(identity, new ExerciseBrief("Stack", "stack", "de.example", null, "Create a stack", ExerciseBrief.Mode.GENERATE), parameters,
                seed, Instant.parse("2026-09-08T12:00:00Z"), IMAGE);
        var command = new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.START, identity, assignment);
        assertThat(codec.decodeCommand(codec.encode(command))).isEqualTo(command);
    }

    @ParameterizedTest
    @ValueSource(strings = { "standard", "" })
    @NullSource
    void outcomeRoundTripPreservesReviewSemantics(String effortProfile) {
        var id = identity();
        var output = new GenerationOutput(new WorkspaceSnapshot(List.of()), new VerificationResult(false, false, false, 0, List.of("No verified candidate")), null,
                new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, "Contradiction", "Review edge cases"))), "RUN_FAILED",
                null, GenerationOutput.AccountingState.INCOMPLETE, effortProfile);
        var event = new WorkerEvent(WorkerCommand.PROTOCOL_VERSION, id.workerId(), id.workerIncarnation(), 1, Instant.now(), WorkerEvent.Type.FINISHED, id, false, IMAGE, null,
                null, output);
        assertThat(codec.decodeEvent(codec.encode(event))).isEqualTo(event);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void heartbeatRoundTripPreservesIdleAndOccupiedCapacity(boolean occupied) {
        var id = identity();
        var event = new WorkerEvent(WorkerCommand.PROTOCOL_VERSION, id.workerId(), id.workerIncarnation(), 1, Instant.now(), WorkerEvent.Type.HEARTBEAT, null, true, IMAGE, null,
                null, null).withCapacity(new WorkerCapacity(4, occupied ? List.of(id) : List.of()));
        assertThat(codec.decodeEvent(codec.encode(event))).isEqualTo(event);
    }

    @Test
    void rejectsUnknownFieldsAndDuplicateKeys() {
        String command = codec.encode(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, identity(), null));
        assertThatThrownBy(() -> codec.decodeCommand(command.replaceFirst("\\{", "{\"shell\":\"execute on host\","))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.decodeCommand(command.replaceFirst("\\{", "{\"protocolVersion\":2,"))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsUnsupportedProtocolVersionsBeforeDispatch() {
        String command = codec.encode(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, identity(), null));
        assertThatThrownBy(() -> codec.decodeCommand(command.replace("\"protocolVersion\":2", "\"protocolVersion\":99"))).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "type", "identity" })
    void rejectsMissingAndNullCommandAuthority(String field) {
        var mapper = new JsonMapper();
        var command = (ObjectNode) mapper.readTree(codec.encode(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, identity(), null)));
        command.putNull(field);
        assertThatThrownBy(() -> codec.decodeCommand(mapper.writeValueAsString(command))).isInstanceOf(RuntimeException.class);
        command.remove(field);
        assertThatThrownBy(() -> codec.decodeCommand(mapper.writeValueAsString(command))).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "executionId", "workerIncarnation" })
    void rejectsMissingAndNullExecutionAuthority(String field) {
        var mapper = new JsonMapper();
        var command = (ObjectNode) mapper.readTree(codec.encode(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, identity(), null)));
        var identity = (ObjectNode) command.get("identity");
        identity.putNull(field);
        assertThatThrownBy(() -> codec.decodeCommand(mapper.writeValueAsString(command))).isInstanceOf(RuntimeException.class);
        identity.remove(field);
        assertThatThrownBy(() -> codec.decodeCommand(mapper.writeValueAsString(command))).isInstanceOf(RuntimeException.class);
    }

    private static ExecutionIdentity identity() {
        return new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker-1", UUID.randomUUID());
    }
}
