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
 * evaluate. The remaining scalars are here because {@code GET exercises/{exerciseId}} is reachable with a SCORPIO tool
 * token from a client that cannot be audited in this tree, and that route used to hand out the {@code ExerciseGroup}
 * entity with its {@code Exam}. It therefore carries the same scalar superset as
 * {@link de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseExamGroupDTO}, read off the entities rather than off
 * a fixture dump. Associations are not repeated: the group's exercises list and the exam's exercise groups, student
 * exams, exam users and exam room assignments are lazy and were never serialized on these paths, and the exam's
 * transient channel name, quiz points and user count are never populated here.
 *
 * @param id          the id of the exercise group
 * @param title       the title of the exercise group
 * @param isMandatory whether the group has to be included in the exam
 * @param exam        the exam the group belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupContextDTO(long id, @Nullable String title, @Nullable Boolean isMandatory, @Nullable ExamContextDTO exam) {

    /**
     * The exam an exam exercise belongs to, as the exercise pages read it.
     *
     * @param id                             the id of the exam
     * @param title                          the title of the exam
     * @param testExam                       whether the exam is a test exam
     * @param examWithAttendanceCheck        whether the exam requires an attendance check
     * @param numberOfCorrectionRoundsInExam the number of correction rounds the assessment dashboard iterates over
     * @param numberOfExercisesInExam        the number of exercises each student exam draws
     * @param visibleDate                    the date the exam becomes visible
     * @param startDate                      the start date of the exam
     * @param endDate                        the end date of the exam, one of the dates the exercise header counts down to
     * @param publishResultsDate             the date results are published, the other date the exercise header counts down to
     * @param examStudentReviewStart         the date the student review period starts
     * @param examStudentReviewEnd           the date the student review period ends
     * @param examSummaryPublicationDate     the date the exam summary is published
     * @param exampleSolutionPublicationDate the date example solutions are published, which gates the example solution page
     * @param gracePeriod                    the grace period in seconds students get after their working time
     * @param workingTime                    the working time of the exam in seconds
     * @param examMaxPoints                  the maximum number of points reachable in the exam
     * @param randomizeExerciseOrder         whether the exercise order is randomized per student
     * @param startText                      the text shown before the exam starts
     * @param endText                        the text shown after the exam ends
     * @param confirmationStartText          the confirmation text students accept before starting
     * @param confirmationEndText            the confirmation text students accept before submitting
     * @param examiner                       the examiner of the exam
     * @param moduleNumber                   the module number of the exam
     * @param courseName                     the course name printed on the exam
     * @param examArchivePath                the path of the exam archive
     * @param course                         the course the exam belongs to
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamContextDTO(long id, @Nullable String title, boolean testExam, boolean examWithAttendanceCheck, @Nullable Integer numberOfCorrectionRoundsInExam,
            @Nullable Integer numberOfExercisesInExam, @Nullable ZonedDateTime visibleDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate,
            @Nullable ZonedDateTime publishResultsDate, @Nullable ZonedDateTime examStudentReviewStart, @Nullable ZonedDateTime examStudentReviewEnd,
            @Nullable ZonedDateTime examSummaryPublicationDate, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable Integer gracePeriod, int workingTime,
            int examMaxPoints, @Nullable Boolean randomizeExerciseOrder, @Nullable String startText, @Nullable String endText, @Nullable String confirmationStartText,
            @Nullable String confirmationEndText, @Nullable String examiner, @Nullable String moduleNumber, @Nullable String courseName, @Nullable String examArchivePath,
            @Nullable ExerciseCourseDTO course) {

        /**
         * Maps an exam to the context the exercise pages read.
         *
         * @param exam the exam, with its eager course loaded
         * @return the exam context
         */
        public static ExamContextDTO of(Exam exam) {
            return new ExamContextDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.isExamWithAttendanceCheck(), exam.getNumberOfCorrectionRoundsInExam(),
                    exam.getNumberOfExercisesInExam(), exam.getVisibleDate(), exam.getStartDate(), exam.getEndDate(), exam.getPublishResultsDate(),
                    exam.getExamStudentReviewStart(), exam.getExamStudentReviewEnd(), exam.getExamSummaryPublicationDate(), exam.getExampleSolutionPublicationDate(),
                    exam.getGracePeriod(), exam.getWorkingTime(), exam.getExamMaxPoints(), exam.getRandomizeExerciseOrder(), exam.getStartText(), exam.getEndText(),
                    exam.getConfirmationStartText(), exam.getConfirmationEndText(), exam.getExaminer(), exam.getModuleNumber(), exam.getCourseName(), exam.getExamArchivePath(),
                    ExerciseCourseDTO.of(exam.getCourse()));
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
        return new ExerciseGroupContextDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exerciseGroup.getIsMandatory(), exam);
    }
}
