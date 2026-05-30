package de.tum.cit.aet.artemis.quiz.dto.exercise;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal exercise-group holder for {@link QuizExerciseForSearchDTO}, preserving the wire path
 * {@code exerciseGroup.exam.course.title} for exam quiz exercises without any entity reference.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSearchExerciseGroupDTO(QuizSearchExamDTO exam) {
}
