package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;

/**
 * Response projection of a {@link StudentExam} for the exam conduction endpoints
 * ({@code .../student-exams/{id}/conduction} and {@code .../test-runs/{id}/conduction}).
 * <p>
 * It is a faithful, byte-compatible projection of the already-masked {@code StudentExam} entity graph the endpoints
 * used to return directly: the exam, exam sessions, the ordered exercises (with their masked quiz questions / stripped
 * programming config), the student participations and their submissions. The {@code prepareStudentExamForConduction}
 * masking runs on the entity <em>before</em> this factory, so the factory only copies fields and re-adds nothing that
 * was stripped. The (unchanged) client keeps typing the response as its full {@code StudentExam} model and reads the
 * preserved fields; the byte-compat oracle tests deserialize the wire back into {@link StudentExam} unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamForConductionDTO(long id, Integer workingTime, Boolean started, ZonedDateTime startedDate, Boolean submitted, ZonedDateTime submissionDate,
        boolean testRun, Boolean ended, boolean finished, Instant createdDate, UserNameDTO user, ExamForConductionDTO exam, List<ExamSessionForConductionDTO> examSessions,
        List<ExamExerciseForConductionDTO> exercises) {

    /**
     * Converts a (masked, conduction-prepared) StudentExam into a StudentExamForConductionDTO.
     *
     * @param studentExam the student exam to convert
     * @return the converted DTO, or null if the student exam is null
     */
    public static StudentExamForConductionDTO of(StudentExam studentExam) {
        if (studentExam == null) {
            return null;
        }
        var entitySessions = studentExam.getExamSessions();
        List<ExamSessionForConductionDTO> examSessions = (entitySessions == null || !Hibernate.isInitialized(entitySessions)) ? null
                : entitySessions.stream().map(ExamSessionForConductionDTO::of).toList();
        var entityExercises = studentExam.getExercises();
        // Single publish gate for quiz solutions, shared with ExamService.loadQuizExercisesForStudentExam: see StudentExam#shouldRevealQuizSolutions.
        boolean includeQuizSolutions = studentExam.shouldRevealQuizSolutions();
        List<ExamExerciseForConductionDTO> exercises = (entityExercises == null || !Hibernate.isInitialized(entityExercises)) ? null
                : entityExercises.stream().filter(Objects::nonNull).map(exercise -> ExamExerciseForConductionDTO.of(exercise, includeQuizSolutions)).toList();
        return new StudentExamForConductionDTO(studentExam.getId(), studentExam.getWorkingTime(), studentExam.isStarted(), studentExam.getStartedDate(), studentExam.isSubmitted(),
                studentExam.getSubmissionDate(), studentExam.isTestRun(), studentExam.isEnded(), studentExam.isFinished(), studentExam.getCreatedDate(),
                UserNameDTO.of(studentExam.getUser()), ExamForConductionDTO.of(studentExam.getExam()), examSessions, exercises);
    }
}
