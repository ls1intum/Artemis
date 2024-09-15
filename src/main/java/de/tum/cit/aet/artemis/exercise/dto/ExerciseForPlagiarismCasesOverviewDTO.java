package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseForPlagiarismCasesOverviewDTO(long id, String title, String type, ExerciseGroupWithIdAndExamDTO exerciseGroup) {
}
