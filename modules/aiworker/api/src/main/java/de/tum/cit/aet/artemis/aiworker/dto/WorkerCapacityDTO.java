package de.tum.cit.aet.artemis.aiworker.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Bounded live occupancy; reservations remain owned by the coordinator. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerCapacityDTO(int slots, List<ExecutionIdentityDTO> executions) {

    public static final int MAX_SLOTS = 16;

    public WorkerCapacityDTO {
        executions = executions == null ? List.of() : List.copyOf(executions);
        if (slots < 1 || slots > MAX_SLOTS || executions.size() > slots || executions.stream().anyMatch(id -> id.slot() >= slots)
                || executions.stream().map(ExecutionIdentityDTO::slot).distinct().count() != executions.size()) {
            throw new IllegalArgumentException("Invalid worker capacity");
        }
    }
}
