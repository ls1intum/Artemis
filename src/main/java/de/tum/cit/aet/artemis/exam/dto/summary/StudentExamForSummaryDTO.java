package de.tum.cit.aet.artemis.exam.dto.summary;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.conduction.ExamExerciseForConductionDTO;
import de.tum.cit.aet.artemis.exam.dto.conduction.ExamSessionForConductionDTO;

/**
 * Response projection of a {@link StudentExam} for the exam summary endpoint
 * ({@code .../student-exams/{id}/summary}), shown to a student after they have submitted their exam.
 * <p>
 * The summary wire is byte-compatible with the conduction wire for the student-facing masked graph the endpoint used
 * to return directly, so it reuses the conduction leaf projections verbatim ({@link ExamExerciseForConductionDTO} for
 * the exercises with their masked/assessed participations, submissions and results; {@link UserNameDTO} for the
 * student; {@link ExamSessionForConductionDTO} for the — for the summary always empty — sessions). The only shape that
 * genuinely differs from conduction is the nested exam, which additionally carries {@code publishResultsDate}
 * ({@link ExamForSummaryDTO}) because the summary client reads it to gate result / example-solution visibility.
 * <p>
 * As with conduction, the {@code fetchParticipationsSubmissionsAndResultsForExam} masking runs on the entity before
 * this factory, so the factory only copies fields and re-adds nothing that was stripped; the (unchanged) client keeps
 * typing the response as its full {@code StudentExam} model, and the summary-semantics oracle tests deserialize the
 * wire back into {@link StudentExam} unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamForSummaryDTO(long id, Integer workingTime, Boolean started, ZonedDateTime startedDate, Boolean submitted, ZonedDateTime submissionDate, boolean testRun,
        Boolean ended, boolean finished, Instant createdDate, UserNameDTO user, ExamForSummaryDTO exam, List<ExamSessionForConductionDTO> examSessions,
        List<ExamExerciseForConductionDTO> exercises) {

    /**
     * Converts a (masked, summary-prepared) StudentExam into a StudentExamForSummaryDTO.
     *
     * @param studentExam the student exam to convert
     * @return the converted DTO, or null if the student exam is null
     */
    public static StudentExamForSummaryDTO of(StudentExam studentExam) {
        if (studentExam == null) {
            return null;
        }
        var entitySessions = studentExam.getExamSessions();
        List<ExamSessionForConductionDTO> examSessions = (entitySessions == null || !Hibernate.isInitialized(entitySessions)) ? null
                : entitySessions.stream().map(ExamSessionForConductionDTO::of).toList();
        var entityExercises = studentExam.getExercises();
        List<ExamExerciseForConductionDTO> exercises = (entityExercises == null || !Hibernate.isInitialized(entityExercises)) ? null
                : entityExercises.stream().map(ExamExerciseForConductionDTO::of).toList();
        return new StudentExamForSummaryDTO(studentExam.getId(), studentExam.getWorkingTime(), studentExam.isStarted(), studentExam.getStartedDate(), studentExam.isSubmitted(),
                studentExam.getSubmissionDate(), studentExam.isTestRun(), studentExam.isEnded(), studentExam.isFinished(), studentExam.getCreatedDate(),
                UserNameDTO.of(studentExam.getUser()), ExamForSummaryDTO.of(studentExam.getExam()), examSessions, exercises);
    }
}
