package de.tum.cit.aet.artemis.hyperion.protocol;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Job-level commands only. No remote shell, file operation or caller-selected sandbox handle is accepted. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerCommand(int protocolVersion, Type type, ExecutionIdentity identity, @Nullable GenerationAssignment assignment) {

    public static final int PROTOCOL_VERSION = 1;

    public WorkerCommand {
        if (type == null || identity == null) {
            throw new IllegalArgumentException("Worker commands require a type and identity");
        }
        if (protocolVersion != PROTOCOL_VERSION || (type == Type.START && (assignment == null || !identity.equals(assignment.identity())))
                || (type != Type.START && assignment != null)) {
            throw new IllegalArgumentException("Incompatible or inconsistent worker command");
        }
    }

    /** Commands are idempotent for the exact assignment identity. */
    public enum Type {
        START, CANCEL, RENEW
    }
}
