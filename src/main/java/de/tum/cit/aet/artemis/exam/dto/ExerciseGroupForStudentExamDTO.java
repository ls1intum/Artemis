package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupForStudentExamDTO(Long id, String title, Boolean isMandatory) {

    public static ExerciseGroupForStudentExamDTO of(ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null) {
            return null;
        }
        return new ExerciseGroupForStudentExamDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exerciseGroup.getIsMandatory());
    }
}
