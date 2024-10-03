package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseExistingExerciseDetails(String[] exerciseTitles, String[] shortNames, String[] channelNames) {
}
