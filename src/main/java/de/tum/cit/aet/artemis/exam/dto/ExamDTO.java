package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Response DTO for the scalar "core" of an {@link Exam}.
 * <p>
 * Returned by the endpoints that never serialize exercise groups: {@code POST} create, {@code PUT} update,
 * {@code PATCH} working-time and the plain {@code GET} by id ({@code withExerciseGroups=false}). It carries exactly the
 * fields the client reads off those responses, matching today's {@code @JsonInclude(NON_EMPTY)} entity wire.
 * <p>
 * The field set is the read side of the edit round-trip: the plain {@code GET} feeds
 * {@code toExamUpdateDTO(exam)} which the edit form {@code PUT}s back. Every field the client maps in that request
 * builder is present here (the {@link de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO} mirror), so a load → save cycle
 * loses nothing. In particular {@code channelName} — the one non-column field, injected on the plain path from the
 * exam's channel — must survive, or the next save silently blanks the exam's communication channel.
 * <p>
 * {@code numberOfExamUsers} and the exercise groups are deliberately not part of this core: no fetch path behind these
 * four endpoints hydrates them, and folding them into a single canonical superset would falsely imply they are always
 * present. The detailed {@code GET} and {@code reset} use {@link ExamWithExerciseGroupsDTO} instead.
 *
 * @param id                             the id of the exam
 * @param title                          the title of the exam
 * @param testExam                       whether this is a test exam (immutable after creation; must round-trip exactly)
 * @param examWithAttendanceCheck        whether an attendance check is enabled
 * @param visibleDate                    the date the exam becomes visible
 * @param startDate                      the exam start date
 * @param endDate                        the exam end date
 * @param publishResultsDate             the date results are published
 * @param examStudentReviewStart         the start of the student review period
 * @param examStudentReviewEnd           the end of the student review period
 * @param gracePeriod                    the grace period in seconds
 * @param workingTime                    the regular working time in seconds
 * @param startText                      the markdown start text
 * @param endText                        the markdown end text
 * @param confirmationStartText          the markdown confirmation start text
 * @param confirmationEndText            the markdown confirmation end text
 * @param examMaxPoints                  the maximum achievable points
 * @param randomizeExerciseOrder         whether the exercise order is randomized per student
 * @param numberOfExercisesInExam        the number of exercises drawn per student exam
 * @param numberOfCorrectionRoundsInExam the number of correction rounds
 * @param examiner                       the examiner
 * @param moduleNumber                   the module number
 * @param courseName                     the course name shown on the exam cover
 * @param exampleSolutionPublicationDate the date example solutions are published
 * @param course                         the slim course projection (id, title, testCourse, group names)
 * @param channelName                    the exam's communication channel name (plain / edit path only; {@code null} elsewhere)
 * @param examArchivePath                the archive path; the archive button reads it (after a re-fetch) to enable download / cleanup
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamDTO(long id, @Nullable String title, boolean testExam, boolean examWithAttendanceCheck, @Nullable ZonedDateTime visibleDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate, @Nullable ZonedDateTime publishResultsDate, @Nullable ZonedDateTime examStudentReviewStart, @Nullable ZonedDateTime examStudentReviewEnd,
        @Nullable Integer gracePeriod, int workingTime, @Nullable String startText, @Nullable String endText, @Nullable String confirmationStartText,
        @Nullable String confirmationEndText, int examMaxPoints, @Nullable Boolean randomizeExerciseOrder, @Nullable Integer numberOfExercisesInExam,
        @Nullable Integer numberOfCorrectionRoundsInExam, @Nullable String examiner, @Nullable String moduleNumber, @Nullable String courseName,
        @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable CourseForExamDTO course, @Nullable String channelName, @Nullable String examArchivePath) {

    /**
     * Builds the scalar-core response DTO from an exam. Reads only stored scalar columns, the eager course, and the
     * (transient) channel name, so it is safe to call on the detached entity returned by the resource outside a
     * transaction. {@code channelName} reflects whatever the entity carries on the current path (set on the plain get,
     * null otherwise), matching today's behaviour.
     *
     * @param exam the exam to convert (with its eager course loaded)
     * @return the scalar-core DTO
     */
    public static ExamDTO of(Exam exam) {
        return new ExamDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.isExamWithAttendanceCheck(), exam.getVisibleDate(), exam.getStartDate(), exam.getEndDate(),
                exam.getPublishResultsDate(), exam.getExamStudentReviewStart(), exam.getExamStudentReviewEnd(), exam.getGracePeriod(), exam.getWorkingTime(), exam.getStartText(),
                exam.getEndText(), exam.getConfirmationStartText(), exam.getConfirmationEndText(), exam.getExamMaxPoints(), exam.getRandomizeExerciseOrder(),
                exam.getNumberOfExercisesInExam(), exam.getNumberOfCorrectionRoundsInExam(), exam.getExaminer(), exam.getModuleNumber(), exam.getCourseName(),
                exam.getExampleSolutionPublicationDate(), CourseForExamDTO.of(exam.getCourse()), exam.getChannelName(), exam.getExamArchivePath());
    }
}
