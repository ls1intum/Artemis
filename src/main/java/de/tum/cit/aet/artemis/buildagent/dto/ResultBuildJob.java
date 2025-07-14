package de.tum.cit.aet.artemis.buildagent.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultBuildJob(@Nullable Long resultId, long programmingExerciseId, String buildJobId) {
}
