package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Worker-authenticated progress or terminal data. The destination, not the payload alone, establishes sender identity. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerEvent(int protocolVersion, String workerId, UUID incarnation, long sequence, Instant timestamp, Type type, @Nullable ExecutionIdentity identity, boolean ready,
        String imageDigest, @Nullable String message, @Nullable GenerationActivity activity, @Nullable GenerationOutput output, @Nullable WorkerCapacity capacity) {

    private static final Pattern WORKER_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private static final Pattern IMAGE_DIGEST = Pattern.compile("sha256:[a-f0-9]{64}");

    public WorkerEvent(int protocolVersion, String workerId, UUID incarnation, long sequence, Instant timestamp, Type type, @Nullable ExecutionIdentity identity, boolean ready,
            String imageDigest, @Nullable String message, @Nullable GenerationActivity activity, @Nullable GenerationOutput output) {
        this(protocolVersion, workerId, incarnation, sequence, timestamp, type, identity, ready, imageDigest, message, activity, output, null);
    }

    public WorkerEvent withCapacity(WorkerCapacity snapshot) {
        return new WorkerEvent(protocolVersion, workerId, incarnation, sequence, timestamp, type, identity, ready, imageDigest, message, activity, output, snapshot);
    }

    public WorkerEvent {
        if (incarnation == null || timestamp == null || type == null) {
            throw new IllegalArgumentException("Worker events require an incarnation, timestamp and type");
        }
        if (protocolVersion != WorkerCommand.PROTOCOL_VERSION || workerId == null || !WORKER_ID.matcher(workerId).matches() || sequence <= 0 || imageDigest == null
                || !IMAGE_DIGEST.matcher(imageDigest).matches() || (message != null && message.length() > 8_192)) {
            throw new IllegalArgumentException("Invalid worker event");
        }
        if (identity != null && (!workerId.equals(identity.workerId()) || !incarnation.equals(identity.workerIncarnation()))) {
            throw new IllegalArgumentException("Event and assignment identities differ");
        }
        if (capacity != null && capacity.executions().stream().anyMatch(id -> !workerId.equals(id.workerId()) || !incarnation.equals(id.workerIncarnation()))) {
            throw new IllegalArgumentException("Capacity contains another worker's execution");
        }
        if ((type != Type.HEARTBEAT && identity == null) || ((type == Type.CHECKPOINT || type == Type.FINISHED) && output == null)) {
            throw new IllegalArgumentException("Event is missing its assignment or candidate");
        }
    }

    /** Events project onto the existing public job contract; these internal values are not public completion states. */
    public enum Type {
        HEARTBEAT, STARTED, PROGRESS, CHECKPOINT, FINISHED, CANCELLED, ERROR
    }
}
