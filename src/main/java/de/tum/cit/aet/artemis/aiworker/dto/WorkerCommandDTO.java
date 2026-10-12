package de.tum.cit.aet.artemis.aiworker.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.aiworker.domain.WorkerCommandType;

/** Idempotent execution-level commands; never shell or filesystem operations. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerCommandDTO(int protocolVersion, WorkerCommandType type, ExecutionIdentityDTO identity, @Nullable ExecutionAssignmentDTO assignment) {

    public static final int PROTOCOL_VERSION = 4;

    public WorkerCommandDTO {
        if (protocolVersion != PROTOCOL_VERSION || type == null || identity == null
                || (type == WorkerCommandType.START ? assignment == null || !identity.equals(assignment.identity()) : assignment != null)) {
            throw new IllegalArgumentException("Incompatible or inconsistent worker command");
        }
    }
}
