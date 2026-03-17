package de.tum.cit.aet.artemis.buildagent.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultBuildJob(@Nullable Long resultId, long programmingExerciseId, String buildJobId) {
}
