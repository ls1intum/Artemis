package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authenticated, bounded capacity snapshot. Core still reserves each slot atomically. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkerCapacity(int slots, List<ExecutionIdentity> executions) {
    public WorkerCapacity {
        if (slots < 1 || slots > 16 || executions == null || executions.size() > slots) {
            throw new IllegalArgumentException("Invalid worker capacity");
        }
        executions = List.copyOf(executions);
        if (executions.stream().anyMatch(id -> id.slot() >= slots)
                || executions.stream().map(ExecutionIdentity::slot).distinct().count() != executions.size()) {
            throw new IllegalArgumentException("An execution must own a distinct configured slot");
        }
    }
}
