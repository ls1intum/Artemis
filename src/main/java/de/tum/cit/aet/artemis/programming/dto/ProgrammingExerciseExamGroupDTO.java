package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Exam exercise-group reference carried on exam programming-exercise responses.
 * <p>
 * Several unchanged Angular views read this nested shape: exam mode is detected from the presence of
 * {@code exercise.exerciseGroup}; the detail page and the navigation utils read
 * {@code exerciseGroup.exam.course.id}; the update form reads {@code exam.course.defaultProgrammingLanguage}; the
 * grading tab reads {@code exerciseGroup.exam.exampleSolutionPublicationDate}; {@code exam.testExam} gates the
 * feedback-suggestion options and {@code exam.numberOfCorrectionRoundsInExam} drives the assessment controls. Flat
 * exam ids are not enough.
 * <p>
 * This chain is also reached from the {@code @AllowedTools(SCORPIO)} latest-result route, whose out-of-repo IntelliJ
 * client cannot be grepped for readers, so both records carry the complete set of serializable {@link ExerciseGroup}
 * and {@link Exam} scalars rather than the handful the Angular client is known to read. The set was taken from the
 * entities, not from a fixture dump, because {@code NON_EMPTY} hides every property a fixture leaves at its default.
 *
 * @param id          the exercise group id
 * @param title       the exercise group title, shown in the exam exercise table
 * @param isMandatory whether the group has to be included in every generated student exam
 * @param exam        the exam reference the client reads
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseExamGroupDTO(Long id, String title, Boolean isMandatory, ProgrammingExerciseExamDTO exam) implements Serializable {

    /**
     * The exam reference carried under {@code exercise.exerciseGroup.exam}.
     * <p>
     * The components are every serializable scalar of {@link Exam} plus the nested course. What the entity serialized
     * here and this record deliberately does not carry is only:
     * <ul>
     * <li>the associations {@code exerciseGroups}, {@code studentExams}, {@code examUsers} and
     * {@code examRoomAssignments}. All four are lazy, none of them is initialized by an exercise fetch graph, and
     * {@code exerciseGroups} would re-enter the very group this exam hangs under.</li>
     * <li>the {@code @Transient} slots {@code numberOfExamUsers}, {@code channelName} and {@code quizExamMaxPoints},
     * which only the exam-management read paths fill in. No path that reaches an exercise sets them, so they were
     * absent from the entity wire here as well.</li>
     * </ul>
     *
     * @param id                             the exam id
     * @param title                          the exam title (detail-page exam link)
     * @param testExam                       whether this is a test exam (gates feedback-suggestion options)
     * @param examWithAttendanceCheck        whether attendance is checked during the exam
     * @param visibleDate                    when the exam becomes visible to students
     * @param startDate                      when students may start working on the exam
     * @param endDate                        when the exam ends
     * @param publishResultsDate             when exam results are published (post-publish behavior)
     * @param examStudentReviewStart         when the student review period starts
     * @param examStudentReviewEnd           when the student review period ends
     * @param examSummaryPublicationDate     when students may see their exam summary
     * @param exampleSolutionPublicationDate when the example solution becomes visible (grading tab)
     * @param gracePeriod                    the extra seconds students get for their final submission
     * @param workingTime                    the default working time in seconds
     * @param startText                      the text shown on the exam start page
     * @param endText                        the text shown on the exam end page
     * @param confirmationStartText          the confirmation text students accept before starting
     * @param confirmationEndText            the confirmation text students accept before submitting
     * @param examMaxPoints                  the reachable points of the exam
     * @param numberOfExercisesInExam        how many exercises a generated student exam contains
     * @param numberOfCorrectionRoundsInExam number of correction rounds (assessment controls)
     * @param randomizeExerciseOrder         whether the exercise order is randomized per student
     * @param examiner                       the name of the examiner
     * @param moduleNumber                   the module number of the exam
     * @param courseName                     the course name printed on the exam
     * @param examArchivePath                the path of the exam archive, when one was created
     * @param course                         light course projection; {@code null} when the exam's course is not loaded
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProgrammingExerciseExamDTO(Long id, String title, Boolean testExam, boolean examWithAttendanceCheck, ZonedDateTime visibleDate, ZonedDateTime startDate,
            ZonedDateTime endDate, ZonedDateTime publishResultsDate, ZonedDateTime examStudentReviewStart, ZonedDateTime examStudentReviewEnd,
            ZonedDateTime examSummaryPublicationDate, ZonedDateTime exampleSolutionPublicationDate, Integer gracePeriod, int workingTime, String startText, String endText,
            String confirmationStartText, String confirmationEndText, int examMaxPoints, Integer numberOfExercisesInExam, Integer numberOfCorrectionRoundsInExam,
            Boolean randomizeExerciseOrder, String examiner, String moduleNumber, String courseName, String examArchivePath, ProgrammingExerciseCourseDTO course)
            implements Serializable {

        /**
         * Converts an {@link Exam} into a {@link ProgrammingExerciseExamDTO}.
         *
         * @param exam   the exam to project
         * @param course the already-projected course of the exam (may be {@code null})
         * @return the projected exam
         */
        public static ProgrammingExerciseExamDTO of(Exam exam, ProgrammingExerciseCourseDTO course) {
            return new ProgrammingExerciseExamDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.isExamWithAttendanceCheck(), exam.getVisibleDate(), exam.getStartDate(),
                    exam.getEndDate(), exam.getPublishResultsDate(), exam.getExamStudentReviewStart(), exam.getExamStudentReviewEnd(), exam.getExamSummaryPublicationDate(),
                    exam.getExampleSolutionPublicationDate(), exam.getGracePeriod(), exam.getWorkingTime(), exam.getStartText(), exam.getEndText(), exam.getConfirmationStartText(),
                    exam.getConfirmationEndText(), exam.getExamMaxPoints(), exam.getNumberOfExercisesInExam(), exam.getNumberOfCorrectionRoundsInExam(),
                    exam.getRandomizeExerciseOrder(), exam.getExaminer(), exam.getModuleNumber(), exam.getCourseName(), exam.getExamArchivePath(), course);
        }
    }

    /**
     * Builds the reference from an {@link ExerciseGroup}. Each of the three levels is guarded separately, so a
     * partially fetched graph degrades to an absent sub-object instead of throwing: an absent or uninitialized group
     * maps to {@code null}, an uninitialized exam to a group with no exam, and an uninitialized course to an exam
     * with no course. Nothing here forces a lazy load.
     *
     * @param exerciseGroup the exercise group (may be {@code null})
     * @return the reference, or {@code null} if the group is {@code null} or not initialized
     */
    public static ProgrammingExerciseExamGroupDTO of(ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null || !Hibernate.isInitialized(exerciseGroup)) {
            return null;
        }
        Exam exam = exerciseGroup.getExam();
        if (exam == null || !Hibernate.isInitialized(exam)) {
            return new ProgrammingExerciseExamGroupDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exerciseGroup.getIsMandatory(), null);
        }
        // Hibernate.isInitialized(null) is true, so the null check has to stand next to it.
        var courseEntity = exam.getCourse();
        ProgrammingExerciseCourseDTO course = courseEntity != null && Hibernate.isInitialized(courseEntity) ? ProgrammingExerciseCourseDTO.of(courseEntity) : null;
        return new ProgrammingExerciseExamGroupDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exerciseGroup.getIsMandatory(), ProgrammingExerciseExamDTO.of(exam, course));
    }

    /**
     * Resolves the {@code exerciseGroup} slot of an exercise response: populated for an exam exercise, {@code null}
     * for a course exercise, which carries its course in the sibling {@code course} slot instead.
     *
     * @param exercise the exercise being mapped
     * @return the nested exercise group, or {@code null} for a course exercise
     */
    public static ProgrammingExerciseExamGroupDTO ofExamExercise(ProgrammingExercise exercise) {
        return exercise.isExamExercise() ? of(exercise.getExerciseGroup()) : null;
    }
}
