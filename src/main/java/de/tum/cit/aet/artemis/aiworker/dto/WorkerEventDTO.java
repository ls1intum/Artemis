package de.tum.cit.aet.artemis.aiworker.dto;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;

/** Authenticated execution evidence. Payload interpretation belongs exclusively to the workload. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerEventDTO(int protocolVersion, String workerId, UUID incarnation, long sequence, Instant timestamp, WorkerEventType type,
        @Nullable ExecutionIdentityDTO identity, boolean ready, String imageDigest, @Nullable String message, @Nullable String payload, WorkerCapacityDTO capacity,
        WorkloadCapabilityDTO capability) {

    public static final int MAX_PAYLOAD_LENGTH = 48 * 1024 * 1024;

    /** Accounting carries usage evidence, not exercise artifacts; keep a broker outage from filling worker memory. */
    public static final int MAX_ACCOUNTING_PAYLOAD_LENGTH = 8 * 1024;

    private static final Pattern WORKER_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private static final Pattern IMAGE_PATTERN = Pattern.compile("sha256:[a-f0-9]{64}");

    public WorkerEventDTO {
        if (protocolVersion != WorkerCommandDTO.PROTOCOL_VERSION || workerId == null || !WORKER_ID_PATTERN.matcher(workerId).matches() || incarnation == null || sequence < 1
                || timestamp == null || type == null || imageDigest == null || !IMAGE_PATTERN.matcher(imageDigest).matches() || capacity == null || capability == null
                || message != null && message.length() > 8192 || payload != null && payload.length() > MAX_PAYLOAD_LENGTH
                || type == WorkerEventType.ACCOUNTING && payload != null && payload.length() > MAX_ACCOUNTING_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Invalid worker event");
        }
        if (identity != null && (!workerId.equals(identity.workerId()) || !incarnation.equals(identity.workerIncarnation()))
                || capacity.executions().stream().anyMatch(id -> !workerId.equals(id.workerId()) || !incarnation.equals(id.workerIncarnation()))
                || type != WorkerEventType.HEARTBEAT && identity == null || (type == WorkerEventType.CHECKPOINT || type == WorkerEventType.FINISHED) && payload == null) {
            throw new IllegalArgumentException("Worker event identity or payload is inconsistent");
        }
    }

    public WorkerEventDTO withCapacity(WorkerCapacityDTO snapshot) {
        return new WorkerEventDTO(protocolVersion, workerId, incarnation, sequence, timestamp, type, identity, ready, imageDigest, message, payload, snapshot, capability);
    }

    public WorkerEventDTO withCapability(WorkloadCapabilityDTO supported) {
        return new WorkerEventDTO(protocolVersion, workerId, incarnation, sequence, timestamp, type, identity, ready, imageDigest, message, payload, capacity, supported);
    }
}
