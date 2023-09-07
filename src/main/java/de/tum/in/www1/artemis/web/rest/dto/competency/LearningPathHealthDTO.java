package de.tum.in.www1.artemis.web.rest.dto.competency;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearningPathHealthDTO(@NotNull HealthStatus status, Long missingLearningPaths) {

    public LearningPathHealthDTO(HealthStatus status) {
        this(status, null);
    }

    public enum HealthStatus {
        OK, DISABLED, MISSING
    }
}
