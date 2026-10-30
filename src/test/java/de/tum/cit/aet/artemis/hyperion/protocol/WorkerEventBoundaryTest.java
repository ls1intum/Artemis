package de.tum.cit.aet.artemis.hyperion.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;

class WorkerEventBoundaryTest {

    private static final String IMAGE = "sha256:" + "a".repeat(64);

    private static final UUID INCARNATION = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final ExecutionIdentity ID = new ExecutionIdentity("job", 7L, UUID.fromString("00000000-0000-0000-0000-000000000002"), "worker", INCARNATION);

    private final WorkerMessageCodec codec = new WorkerMessageCodec();

    private final WorkerMessageCodecApi wireCodec = new WorkerMessageCodecApi();

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEvents")
    void rejectsMalformedOrForeignAuthoringEvidence(String reason, Consumer<Event> mutate) {
        Event event = new Event();
        assertThat(event.build().type()).isEqualTo(WorkerEvent.Type.PROGRESS);
        mutate.accept(event);
        assertThatThrownBy(event::build).as(reason).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> invalidEvents() {
        return Stream.of(invalid("missing incarnation", event -> event.incarnation = null), invalid("missing timestamp", event -> event.timestamp = null),
                invalid("missing type", event -> event.type = null), invalid("missing toolchain", event -> event.toolchain = null),
                invalid("unsupported protocol", event -> event.protocol = -1), invalid("missing worker", event -> event.worker = null),
                invalid("unsafe worker", event -> event.worker = "../worker"), invalid("nonpositive sequence", event -> event.sequence = 0),
                invalid("missing image", event -> event.image = null), invalid("mutable image", event -> event.image = "latest"),
                invalid("oversized message", event -> event.message = "x".repeat(8193)), invalid("missing assignment", event -> event.identity = null),
                invalid("foreign assignment worker", event -> event.identity = new ExecutionIdentity("job", 7, UUID.randomUUID(), "other", INCARNATION)),
                invalid("foreign assignment incarnation", event -> event.identity = new ExecutionIdentity("job", 7, UUID.randomUUID(), "worker", UUID.randomUUID())),
                invalid("foreign capacity worker",
                        event -> event.capacity = new WorkerCapacity(1, List.of(new ExecutionIdentity("job", 7, UUID.randomUUID(), "other", INCARNATION)))),
                invalid("foreign capacity incarnation",
                        event -> event.capacity = new WorkerCapacity(1, List.of(new ExecutionIdentity("job", 7, UUID.randomUUID(), "worker", UUID.randomUUID())))),
                invalid("checkpoint requires output", event -> event.type = WorkerEvent.Type.CHECKPOINT),
                invalid("finished requires output", event -> event.type = WorkerEvent.Type.FINISHED));
    }

    private static Arguments invalid(String label, Consumer<Event> mutation) {
        return Arguments.of(label, mutation);
    }

    @Test
    void heartbeatDefaultsIncludeOnlyItsCurrentExecutionAndCopiesPreserveEvidence() {
        Event fixture = new Event();
        fixture.type = WorkerEvent.Type.HEARTBEAT;
        fixture.message = "x".repeat(8192);
        WorkerEvent event = fixture.build();
        assertThat(event.capacity().executions()).containsExactly(ID);
        assertThat(event.withCapacity(new WorkerCapacity(2, List.of())).capacity().slots()).isEqualTo(2);
        assertThat(event.capacity().slots()).isOne();
        fixture.identity = null;
        assertThat(fixture.build().capacity().executions()).isEmpty();
        assertThat(event.withToolchain(new GenerationToolchain("python-pytest")).toolchain().id()).isEqualTo("python-pytest");
        assertThat(event.withToolchain(new GenerationToolchain("python-pytest")).message()).hasSize(8192);
    }

    @Test
    void progressAndAccountingRoundTripsPreserveActivityAndNeutralEventType() {
        Event fixture = new Event();
        fixture.activity = new GenerationActivity("Generating tests", 1, 2, true, 3, 4, 5);
        WorkerEvent event = fixture.build().withProgress(new GenerationProgress("VERIFYING", null, null, null));
        assertThat(codec.fromWire(wireCodec.decodeEvent(wireCodec.encode(codec.toWire(event))))).isEqualTo(event);
        var accounting = fixture.build().withProgress(new GenerationProgress(null, null, null, new ProviderUsageUpdate(ProviderUsageUpdate.Kind.TURN, 1, null)));
        WorkerEventDTO wire = codec.toWire(accounting);
        assertThat(wire.type()).isEqualTo(WorkerEventType.ACCOUNTING);
        assertThat(codec.fromWire(wire)).isEqualTo(accounting);
        assertThat(codec.fromWire(wireCodec.decodeEvent(wireCodec.encode(codec.toWire(fixture.build()))))).isEqualTo(fixture.build());
    }

    @ParameterizedTest
    @ValueSource(strings = { "identity", "capability", "image", "deadline" })
    void assignmentPayloadCannotOverrideAuthorizedEnvelope(String field) {
        var parameters = new GenerationParameters(null, 10, 100000, Duration.ofMinutes(5), 128000, null, null, null, null, null, true, "CONTINUOUS");
        var deadline = Instant.parse("2030-01-01T00:00:00Z");
        var assignment = new GenerationAssignment(ID, new ExerciseBrief("Title", "short", "de.example", null, "Generate", ExerciseBrief.Mode.GENERATE), parameters,
                new WorkspaceSnapshot(List.of()), deadline, IMAGE, new GradingContext(false, java.util.Set.of(), java.util.Set.of()));
        var authorized = new ExecutionAssignmentDTO(WorkerMessageCodec.toWire(ID), WorkerMessageCodec.capability(GenerationToolchain.JAVA_GRADLE), deadline, IMAGE,
                codec.encodePayload(assignment));
        var envelope = new ExecutionAssignmentDTO(
                field.equals("identity") ? WorkerMessageCodec.toWire(new ExecutionIdentity("other", 7, UUID.randomUUID(), "worker", INCARNATION)) : authorized.identity(),
                field.equals("capability") ? new WorkloadCapabilityDTO("hyperion-generation", 1, "python-pytest") : authorized.capability(),
                field.equals("deadline") ? deadline.plusSeconds(1) : deadline, field.equals("image") ? "sha256:" + "b".repeat(64) : IMAGE, authorized.payload());
        assertThatThrownBy(() -> codec.decodeAssignment(envelope)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hyperion payload differs from its authorized execution envelope");
        assertThat(codec.decodeAssignment(authorized)).isEqualTo(assignment);
    }

    @ParameterizedTest
    @ValueSource(strings = { "workload", "version" })
    void rejectsAnotherWorkloadBeforeParsingItsEvidence(String field) {
        var wire = codec.toWire(new Event().build());
        var foreign = wire.withCapability(new WorkloadCapabilityDTO(field.equals("workload") ? "other" : "hyperion-generation", field.equals("version") ? 2 : 1, "java-gradle"));
        assertThatThrownBy(() -> codec.fromWire(foreign)).isInstanceOf(IllegalArgumentException.class).hasMessage("Event belongs to another workload schema");
    }

    @Test
    void stopAuthoringUsesNeutralFinishCommandWithoutChangingItsAssignmentIdentity() {
        var command = new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.STOP_AUTHORING, ID, null);
        assertThat(codec.toWire(command).type().name()).isEqualTo("FINISH");
        var decoded = wireCodec.decodeCommand(wireCodec.encode(codec.toWire(command)));
        assertThat(decoded.type().name()).isEqualTo("FINISH");
        assertThat(decoded.protocolVersion()).isEqualTo(command.protocolVersion());
        assertThat(WorkerMessageCodec.fromWire(decoded.identity())).isEqualTo(command.identity());
        assertThat(decoded.assignment()).isNull();
        assertThat(WorkerMessageCodec.fromWire(WorkerMessageCodec.toWire(ID))).isEqualTo(ID);
    }

    private static class Event {

        int protocol = WorkerCommand.PROTOCOL_VERSION;

        String worker = "worker";

        UUID incarnation = INCARNATION;

        long sequence = 1;

        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");

        WorkerEvent.Type type = WorkerEvent.Type.PROGRESS;

        ExecutionIdentity identity = ID;

        String image = IMAGE;

        String message;

        GenerationActivity activity;

        WorkerCapacity capacity;

        GenerationToolchain toolchain = GenerationToolchain.JAVA_GRADLE;

        WorkerEvent build() {
            return new WorkerEvent(protocol, worker, incarnation, sequence, timestamp, type, identity, true, image, message, activity, null, null, capacity, toolchain);
        }
    }
}
