package de.tum.cit.aet.artemis.atlas.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathHealthDTO(@NotEmpty Set<HealthStatus> status, Long missingLearningPaths) {

    public LearningPathHealthDTO(Set<HealthStatus> status) {
        this(status, null);
    }

    public enum HealthStatus {
        OK, DISABLED, MISSING, NO_COMPETENCIES, NO_RELATIONS
    }
}
