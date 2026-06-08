package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents one row in the exam-students management table.
 * Combines registration and room/seat/attendance data from {@link de.tum.cit.aet.artemis.exam.domain.ExamUser}
 * with working-time and submission-progress data from the corresponding non-test-run
 * {@link de.tum.cit.aet.artemis.exam.domain.StudentExam}.
 * All student-exam fields are nullable: they are {@code null} when no student exam has been generated for the student yet.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamStudentDTO(
        // ExamUser fields
        Long id, Long userId, String login, String name, String email, String visibleRegistrationNumber, String studentImagePath, String plannedRoom, String actualRoom,
        String plannedSeat, String actualSeat, Boolean didCheckImage, Boolean didCheckName, Boolean didCheckLogin, Boolean didCheckRegistrationNumber, String signingImagePath,
        Boolean didExamUserAttendExam,
        // StudentExam fields
        Long studentExamId, Integer workingTime, Boolean started, Boolean submitted, ZonedDateTime startedDate, ZonedDateTime submissionDate, Long numberOfExamSessions,
        String progress) {

    /** Progress keys, matching the frontend ExamProgress type. */
    public static final String PROGRESS_EXAM_MISSING = "examMissing";

    public static final String PROGRESS_NOT_STARTED = "notStarted";

    public static final String PROGRESS_STARTED = "started";

    public static final String PROGRESS_SUBMITTED = "submitted";

    /**
     * Projection of the student-exam fields needed to build an {@link ExamStudentDTO}.
     * Fetched via a JPQL constructor expression scoped to the current page's user IDs,
     * with {@code examSessionCount} returned as a {@code COUNT} aggregate rather than loading session entities.
     */
    public record StudentExamSummary(Long userId, Long studentExamId, Integer workingTime, Boolean started, Boolean submitted, ZonedDateTime startedDate,
            ZonedDateTime submissionDate, Long examSessionCount) {
    }
}
