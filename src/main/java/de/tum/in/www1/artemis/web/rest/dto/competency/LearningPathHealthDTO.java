package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearningPathHealthDTO(@NotEmpty Set<HealthStatus> status, Long missingLearningPaths) {

    public LearningPathHealthDTO(Set<HealthStatus> status) {
        this(status, null);
    }

    public enum HealthStatus {
        OK, DISABLED, MISSING, NO_COMPETENCIES, NO_RELATIONS
    }
}
