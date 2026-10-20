package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.List;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerCommandType;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;

/** Fixed JSON wire format, independent of either application's HTTP mapper or Java serialization. */
public final class WorkerMessageCodec {

    public static final int MAX_MESSAGE_CHARS = WorkerMessageCodecApi.MAX_MESSAGE_CHARS;

    private final WorkerMessageCodecApi wire = new WorkerMessageCodecApi();

    private final JsonMapper mapper = JsonMapper
            .builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(12 * 1024 * 1024).maxNumberLength(32).build()).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public String encode(WorkerCommand command) {
        return wire.encode(toWire(command));
    }

    public String encode(WorkerEvent event) {
        return wire.encode(toWire(event));
    }

    public WorkerCommand decodeCommand(String body) {
        return fromWire(wire.decodeCommand(body));
    }

    public WorkerEvent decodeEvent(String body) {
        return fromWire(wire.decodeEvent(body));
    }

    /**
     * Encodes a typed authoring command in the workload-neutral execution envelope.
     *
     * @param command typed authoring command
     * @return neutral transport command
     */
    public WorkerCommandDTO toWire(WorkerCommand command) {
        var assignment = command.assignment();
        return new WorkerCommandDTO(WorkerCommand.PROTOCOL_VERSION,
                WorkerCommandType.valueOf(command.type() == WorkerCommand.Type.STOP_AUTHORING ? "FINISH" : command.type().name()), toWire(command.identity()),
                assignment == null ? null
                        : new ExecutionAssignmentDTO(toWire(assignment.identity()), capability(assignment.brief().toolchain()), assignment.authoringDeadline(),
                                assignment.imageDigest(), encodePayload(assignment)));
    }

    public WorkerCommand fromWire(WorkerCommandDTO command) {
        GenerationAssignment assignment = command.assignment() == null ? null : decodeAssignment(command.assignment());
        return new WorkerCommand(command.protocolVersion(), WorkerCommand.Type.valueOf(command.type().name().equals("FINISH") ? "STOP_AUTHORING" : command.type().name()),
                fromWire(command.identity()), assignment);
    }

    /**
     * Decodes authoring input and rejects authority fields that differ from the execution envelope.
     *
     * @param envelope authorized execution envelope
     * @return validated authoring input
     */
    public GenerationAssignment decodeAssignment(ExecutionAssignmentDTO envelope) {
        GenerationAssignment assignment = decodePayload(envelope.payload(), GenerationAssignment.class);
        if (!toWire(assignment.identity()).equals(envelope.identity()) || !capability(assignment.brief().toolchain()).equals(envelope.capability())
                || !assignment.imageDigest().equals(envelope.imageDigest()) || !assignment.authoringDeadline().equals(envelope.deadline())) {
            throw new IllegalArgumentException("Hyperion payload differs from its authorized execution envelope");
        }
        return assignment;
    }

    /**
     * Encodes domain evidence while preserving reliable accounting semantics.
     *
     * @param event typed authoring evidence
     * @return neutral transport event
     */
    public WorkerEventDTO toWire(WorkerEvent event) {
        String payload = event.output() != null ? encodePayload(event.output())
                : event.activity() != null || event.progress() != null ? encodePayload(new GenerationEventPayloadDTO(event.activity(), event.progress())) : null;
        var capacity = event.capacity() == null ? new WorkerCapacity(1, List.of()) : event.capacity();
        return new WorkerEventDTO(event.protocolVersion(), event.workerId(), event.incarnation(), event.sequence(), event.timestamp(),
                WorkerEventType.valueOf(event.progress() != null && event.progress().usage() != null ? "ACCOUNTING" : event.type().name()),
                event.identity() == null ? null : toWire(event.identity()), event.ready(), event.imageDigest(), event.message(), payload,
                new WorkerCapacityDTO(capacity.slots(), capacity.executions().stream().map(WorkerMessageCodec::toWire).toList()), capability(event.toolchain()));
    }

    /**
     * Rejects other workload schemas before decoding Hyperion evidence.
     *
     * @param event neutral transport event
     * @return validated Hyperion evidence
     */
    public WorkerEvent fromWire(WorkerEventDTO event) {
        if (!event.capability().workload().equals("hyperion-generation") || event.capability().version() != 1) {
            throw new IllegalArgumentException("Event belongs to another workload schema");
        }
        WorkerEvent.Type type = WorkerEvent.Type.valueOf(event.type().name().equals("ACCOUNTING") ? "PROGRESS" : event.type().name());
        GenerationOutput output = event.payload() != null && (type == WorkerEvent.Type.CHECKPOINT || type == WorkerEvent.Type.FINISHED || type == WorkerEvent.Type.CANCELLED)
                ? decodePayload(event.payload(), GenerationOutput.class)
                : null;
        GenerationEventPayloadDTO detail = event.payload() != null && type == WorkerEvent.Type.PROGRESS ? decodePayload(event.payload(), GenerationEventPayloadDTO.class) : null;
        return new WorkerEvent(event.protocolVersion(), event.workerId(), event.incarnation(), event.sequence(), event.timestamp(), type,
                event.identity() == null ? null : fromWire(event.identity()), event.ready(), event.imageDigest(), event.message(), detail == null ? null : detail.activity(),
                output, detail == null ? null : detail.progress(),
                new WorkerCapacity(event.capacity().slots(), event.capacity().executions().stream().map(WorkerMessageCodec::fromWire).toList()),
                new GenerationToolchain(event.capability().profile()));
    }

    public String encodePayload(Object payload) {
        return mapper.writeValueAsString(payload);
    }

    public <T> T decodePayload(String payload, Class<T> type) {
        return mapper.readValue(payload, type);
    }

    public static WorkloadCapabilityDTO capability(GenerationToolchain toolchain) {
        return new WorkloadCapabilityDTO("hyperion-generation", 1, toolchain.id());
    }

    public static ExecutionIdentityDTO toWire(ExecutionIdentity identity) {
        return new ExecutionIdentityDTO(identity.jobId(), Long.toString(identity.exerciseId()), identity.executionId(), identity.workerId(), identity.workerIncarnation(),
                identity.slot());
    }

    public static ExecutionIdentity fromWire(ExecutionIdentityDTO identity) {
        return new ExecutionIdentity(identity.jobId(), Long.parseLong(identity.resourceId()), identity.executionId(), identity.workerId(), identity.workerIncarnation(),
                identity.slot());
    }
}
