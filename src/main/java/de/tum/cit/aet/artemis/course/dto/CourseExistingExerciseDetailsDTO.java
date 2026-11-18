package de.tum.cit.aet.artemis.course.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseExistingExerciseDetailsDTO(Set<String> exerciseTitles, Set<String> shortNames) {
}
