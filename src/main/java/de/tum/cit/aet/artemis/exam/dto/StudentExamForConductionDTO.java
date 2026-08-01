package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;

/**
 * Response projection of the own {@link StudentExam} returned by the exam-conduction entry point
 * ({@code GET courses/{courseId}/exams/{examId}/own-student-exam},
 * {@link de.tum.cit.aet.artemis.exam.web.ExamResource#getOwnStudentExam}).
 * <p>
 * The response carries the student exam <em>without exercises</em> — the exercise graph is loaded separately once the
 * student clicks start (the {@code .../conduction} endpoint). It mirrors the scalar shape of the shared
 * {@link StudentExamDTO} (used by the management/test-run endpoints) and reuses the same {@link UserNameDTO} leaf, but
 * nests the richer {@link ExamForConductionDTO} instead of the slim {@link ExamForStudentExamDTO}, because the conduction
 * cover reads the full exam cover metadata (see {@link ExamForConductionDTO}).
 * <p>
 * The client reads, off this response: {@code exam} (the cover), {@code user.name} (the examined-student box),
 * {@code workingTime}, {@code started}/{@code startedDate}, {@code submitted}/{@code submissionDate}, {@code testRun}
 * (navbar / test-run branch) and {@code ended}. {@code ended} is a server-computed flag ({@link StudentExam#isEnded()});
 * the client trusts it as authoritative in {@code isOver()} and otherwise falls back to its own time comparison, so it is
 * carried here (and dropped by {@code NON_EMPTY} when the server cannot determine it, exactly as the entity wire did).
 *
 * @param id             the id of the student exam
 * @param workingTime    the individual working time in seconds
 * @param started        whether the student exam has been started
 * @param startedDate    the date the student exam was started
 * @param submitted      whether the student exam has been submitted
 * @param submissionDate the date the student exam was submitted
 * @param testRun        whether this is a test run
 * @param ended          whether the individual student exam has ended (server-computed; {@code null} if undeterminable)
 * @param user           the identifying name of the student ({@code name} shown on the cover)
 * @param exam           the rich exam projection read by the conduction cover
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamForConductionDTO(long id, @Nullable Integer workingTime, @Nullable Boolean started, @Nullable ZonedDateTime startedDate, @Nullable Boolean submitted,
        @Nullable ZonedDateTime submissionDate, boolean testRun, @Nullable Boolean ended, @Nullable UserNameDTO user, @Nullable ExamForConductionDTO exam) {

    /**
     * Converts a StudentExam into a StudentExamForConductionDTO.
     *
     * @param studentExam the student exam to convert
     * @return the converted DTO, or {@code null} if the student exam is {@code null}
     */
    @Nullable
    public static StudentExamForConductionDTO of(@Nullable StudentExam studentExam) {
        if (studentExam == null) {
            return null;
        }
        return new StudentExamForConductionDTO(studentExam.getId(), studentExam.getWorkingTime(), studentExam.isStarted(), studentExam.getStartedDate(), studentExam.isSubmitted(),
                studentExam.getSubmissionDate(), studentExam.isTestRun(), studentExam.isEnded(), UserNameDTO.of(studentExam.getUser()),
                ExamForConductionDTO.of(studentExam.getExam()));
    }
}
