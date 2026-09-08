package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Worker-authenticated progress or terminal data. The destination, not the payload alone, establishes sender identity. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerEvent(int protocolVersion, String workerId, UUID incarnation, long sequence, Instant timestamp, Type type, @Nullable ExecutionIdentity identity, boolean ready,
        String imageDigest, @Nullable String message, @Nullable GenerationActivity activity, @Nullable GenerationOutput output, @Nullable GenerationProgress progress) {

    public WorkerEvent {
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(type);
        if (protocolVersion != WorkerCommand.PROTOCOL_VERSION || workerId == null || !workerId.matches("[a-zA-Z0-9_-]{1,64}") || sequence <= 0 || imageDigest == null
                || !imageDigest.matches("sha256:[a-f0-9]{64}") || (message != null && message.length() > 8_192)) {
            throw new IllegalArgumentException("Invalid worker event");
        }
        if (identity != null && (!workerId.equals(identity.workerId()) || !incarnation.equals(identity.workerIncarnation()))) {
            throw new IllegalArgumentException("Event and assignment identities differ");
        }
        if ((type != Type.HEARTBEAT && identity == null) || ((type == Type.CHECKPOINT || type == Type.FINISHED) && output == null)) {
            throw new IllegalArgumentException("Event is missing its assignment or candidate");
        }
    }

    public WorkerEvent(int protocolVersion, String workerId, UUID incarnation, long sequence, Instant timestamp, Type type, @Nullable ExecutionIdentity identity, boolean ready,
            String imageDigest, @Nullable String message, @Nullable GenerationActivity activity, @Nullable GenerationOutput output) {
        this(protocolVersion, workerId, incarnation, sequence, timestamp, type, identity, ready, imageDigest, message, activity, output, null);
    }

    public WorkerEvent withProgress(GenerationProgress detail) {
        return new WorkerEvent(protocolVersion, workerId, incarnation, sequence, timestamp, type, identity, ready, imageDigest, message, activity, output, detail);
    }

    /** Events project onto the existing public job contract; these internal values are not public completion states. */
    public enum Type {
        HEARTBEAT, STARTED, PROGRESS, CHECKPOINT, FINISHED, CANCELLED, ERROR
    }
}
