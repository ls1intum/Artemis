package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Minimal exam exercise-group reference carried on exam text-exercise responses. The student text editor and exam
 * result summary detect exam mode from the presence of {@code exercise.exerciseGroup} and read
 * {@code exercise.exerciseGroup.exam.publishResultsDate} to decide post-publish behavior; the flat exam ids are not
 * enough because the unchanged client reads this nested shape. Carries only those fields.
 *
 * @param id   the exercise group id
 * @param exam the minimal exam reference (id and publish-results date)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseExamGroupDTO(Long id, ExamReferenceDTO exam) implements Serializable {

    /**
     * Minimal exam reference: id and the publish-results date the client uses for post-publish behavior.
     *
     * @param id                 the exam id
     * @param publishResultsDate when exam results are published
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamReferenceDTO(Long id, ZonedDateTime publishResultsDate) implements Serializable {
    }

    /**
     * Builds the reference from an {@link ExerciseGroup}; expects the group (and its exam) to be loaded.
     *
     * @param exerciseGroup the exercise group (may be {@code null})
     * @return the reference, or {@code null} if the input was {@code null}
     */
    public static TextExerciseExamGroupDTO of(ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null) {
            return null;
        }
        Exam exam = exerciseGroup.getExam();
        ExamReferenceDTO examRef = exam == null ? null : new ExamReferenceDTO(exam.getId(), exam.getPublishResultsDate());
        return new TextExerciseExamGroupDTO(exerciseGroup.getId(), examRef);
    }
}
