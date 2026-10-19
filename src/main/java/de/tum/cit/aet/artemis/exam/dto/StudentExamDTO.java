package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;

/**
 * Shared response projection of a {@link StudentExam}, used by the management/test-run endpoints in
 * {@code StudentExamResource} (student-exams list, working-time update, test-exams-per-user, test-runs list,
 * create-test-run, toggle-to-submitted/-unsubmitted).
 * <p>
 * {@code user} and {@code exam} are only populated by the factory methods that name them explicitly ({@link #withUser}
 * / {@link #withExam}); {@link #of} leaves both {@code null} (dropped by {@code NON_EMPTY}), mirroring the masking
 * ({@code studentExam.setExam(null)}) or plain non-inclusion that each of these endpoints already applies today.
 * <p>
 * Never touches {@code exercises}, {@code examSessions}, or {@code studentParticipations}: those are lazy
 * associations that are not guaranteed to be fetched by every query backing these endpoints (OSIV is off), and none
 * of the seven in-scope endpoints serialize them today.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamDTO(long id, Integer workingTime, Boolean started, ZonedDateTime startedDate, Boolean submitted, ZonedDateTime submissionDate, boolean testRun,
        UserNameDTO user, ExamForStudentExamDTO exam) {

    /**
     * Converts a StudentExam into a StudentExamDTO without the nested user or exam.
     *
     * @param studentExam the student exam to convert
     * @return the converted DTO, or null if the student exam is null
     */
    public static StudentExamDTO of(StudentExam studentExam) {
        return of(studentExam, false, false);
    }

    /**
     * Converts a StudentExam into a StudentExamDTO including the nested user (e.g. for the test-run list/creation
     * endpoints, whose client template reads {@code user.name}/{@code user.id}).
     *
     * @param studentExam the student exam to convert
     * @return the converted DTO, or null if the student exam is null
     */
    public static StudentExamDTO withUser(StudentExam studentExam) {
        return of(studentExam, true, false);
    }

    /**
     * Converts a StudentExam into a StudentExamDTO including the nested exam (and its course), e.g. for endpoints
     * whose client needs {@code exam.course} to compute course-level access rights.
     *
     * @param studentExam the student exam to convert
     * @return the converted DTO, or null if the student exam is null
     */
    public static StudentExamDTO withExam(StudentExam studentExam) {
        return of(studentExam, false, true);
    }

    private static StudentExamDTO of(StudentExam studentExam, boolean includeUser, boolean includeExam) {
        if (studentExam == null) {
            return null;
        }
        return new StudentExamDTO(studentExam.getId(), studentExam.getWorkingTime(), studentExam.isStarted(), studentExam.getStartedDate(), studentExam.isSubmitted(),
                studentExam.getSubmissionDate(), studentExam.isTestRun(), includeUser ? UserNameDTO.of(studentExam.getUser()) : null,
                includeExam ? ExamForStudentExamDTO.of(studentExam.getExam()) : null);
    }
}
