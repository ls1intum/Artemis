package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * The exam context an exam exercise carries on {@code exercise.exerciseGroup}.
 * <p>
 * The exercise pages use it for three things: the group title they show instead of the exercise title, the exam and
 * course ids every management link is built from, and the exam dates the exercise header and the assessment dashboard
 * evaluate. The group's exercises list is not part of it; it is lazy and was never serialized on these paths.
 *
 * @param id    the id of the exercise group
 * @param title the title of the exercise group
 * @param exam  the exam the group belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupContextDTO(long id, @Nullable String title, @Nullable ExamContextDTO exam) {

    /**
     * The exam an exam exercise belongs to, as the exercise pages read it.
     *
     * @param id                             the id of the exam
     * @param title                          the title of the exam
     * @param testExam                       whether the exam is a test exam
     * @param numberOfCorrectionRoundsInExam the number of correction rounds the assessment dashboard iterates over
     * @param endDate                        the end date of the exam, one of the dates the exercise header counts down to
     * @param publishResultsDate             the date results are published, the other date the exercise header counts down to
     * @param exampleSolutionPublicationDate the date example solutions are published, which gates the example solution page
     * @param course                         the course the exam belongs to
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamContextDTO(long id, @Nullable String title, boolean testExam, @Nullable Integer numberOfCorrectionRoundsInExam, @Nullable ZonedDateTime endDate,
            @Nullable ZonedDateTime publishResultsDate, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable ExerciseCourseDTO course) {

        /**
         * Maps an exam to the context the exercise pages read.
         *
         * @param exam the exam, with its eager course loaded
         * @return the exam context
         */
        public static ExamContextDTO of(Exam exam) {
            return new ExamContextDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.getNumberOfCorrectionRoundsInExam(), exam.getEndDate(), exam.getPublishResultsDate(),
                    exam.getExampleSolutionPublicationDate(), ExerciseCourseDTO.of(exam.getCourse()));
        }
    }

    /**
     * Maps an exercise group to the context the exercise pages read.
     *
     * @param exerciseGroup the exercise group, with its eager exam loaded, may be {@code null} for a course exercise
     * @return the exercise group context, or {@code null} for a course exercise
     */
    public static @Nullable ExerciseGroupContextDTO of(@Nullable ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null) {
            return null;
        }
        ExamContextDTO exam = exerciseGroup.getExam() == null ? null : ExamContextDTO.of(exerciseGroup.getExam());
        return new ExerciseGroupContextDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exam);
    }
}
