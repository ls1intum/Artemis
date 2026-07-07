package de.tum.cit.aet.artemis.quiz.dto.exercise;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal exam holder for {@link QuizExerciseForSearchDTO}. Preserves the wire path
 * {@code exerciseGroup.exam.course.title} (and exposes the exam {@code title} used for sorting) without any entity
 * reference.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSearchExamDTO(String title, QuizSearchCourseDTO course) {
}
