package de.tum.cit.aet.artemis.exam.dto.detail;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.dto.conduction.ExamExerciseForConductionDTO;
import de.tum.cit.aet.artemis.exam.dto.conduction.ExamForConductionDTO;

/**
 * Response projection of a {@link StudentExam} as carried in the {@code studentExam} field of
 * {@link StudentExamWithGradeDTO}.
 * <p>
 * This replaces the raw {@code StudentExam} entity that field used to hold. The real reader is the instructor-facing
 * student-exam detail screen (served by the {@code getStudentExam} endpoint, whose graph is <em>not</em> masked): it
 * reads the full examined-student identity ({@link UserForDetailDTO}), the full proctoring session table
 * ({@link ExamSessionForDetailDTO}), the exam (with its course) and the per-exercise participation / submission /
 * result graph. The grade-summary endpoint populates the same field for a student caller (masked graph), but the
 * client discards it there and reloads the exam via the separate {@code /summary} fetch, so only the id round-trips.
 * <p>
 * The exercise / exam graph is byte-compatible with the already-shipped conduction projection (results are
 * assessor-/feedback-stripped by {@code setResultIfNecessary} in both graphs, and the detail client does not read the
 * exercise-group back-reference the conduction leaf drops), so those leaves are reused verbatim. Only the user and
 * session leaves are richer than the student-facing conduction variants, because the instructor sees more. All leaf
 * factories are back-reference-free, so this projection is also safe on the masked grade-summary graph (no
 * {@code getCourseViaExerciseGroupOrCourseMember} NPE).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamForDetailDTO(long id, Integer workingTime, Boolean started, ZonedDateTime startedDate, Boolean submitted, ZonedDateTime submissionDate, boolean testRun,
        Boolean ended, boolean finished, Instant createdDate, UserForDetailDTO user, ExamForConductionDTO exam, List<ExamSessionForDetailDTO> examSessions,
        List<ExamExerciseForConductionDTO> exercises) {

    /**
     * Converts a StudentExam into a StudentExamForDetailDTO.
     *
     * @param studentExam the student exam to convert
     * @return the converted DTO, or null if the student exam is null
     */
    public static StudentExamForDetailDTO of(StudentExam studentExam) {
        if (studentExam == null) {
            return null;
        }
        var entitySessions = studentExam.getExamSessions();
        List<ExamSessionForDetailDTO> examSessions = (entitySessions == null || !Hibernate.isInitialized(entitySessions)) ? null
                : entitySessions.stream().map(ExamSessionForDetailDTO::of).toList();
        var entityExercises = studentExam.getExercises();
        List<ExamExerciseForConductionDTO> exercises = (entityExercises == null || !Hibernate.isInitialized(entityExercises)) ? null
                : entityExercises.stream().map(ExamExerciseForConductionDTO::of).toList();
        return new StudentExamForDetailDTO(studentExam.getId(), studentExam.getWorkingTime(), studentExam.isStarted(), studentExam.getStartedDate(), studentExam.isSubmitted(),
                studentExam.getSubmissionDate(), studentExam.isTestRun(), studentExam.isEnded(), studentExam.isFinished(), studentExam.getCreatedDate(),
                UserForDetailDTO.of(studentExam.getUser()), ExamForConductionDTO.of(studentExam.getExam()), examSessions, exercises);
    }
}
