package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamSession;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseForStudentExamDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamForResponseDTO(Long id, Boolean submitted, Integer workingTime, Boolean started, ZonedDateTime startedDate, ZonedDateTime submissionDate, Boolean testRun,
        Boolean ended, User user, Exam exam, List<Object> exercises, Set<ExamSession> examSessions) {

    /**
     * Creates a student exam response DTO and maps quiz exercises to solution-aware DTOs.
     *
     * @param studentExam      the student exam to map
     * @param includeSolutions whether quiz solution fields should be included in the response
     * @return the response DTO
     */
    public static StudentExamForResponseDTO of(StudentExam studentExam, boolean includeSolutions) {
        List<Object> exerciseDTOs = studentExam.getExercises().stream().map(exercise -> mapExercise(exercise, includeSolutions)).toList();
        return new StudentExamForResponseDTO(studentExam.getId(), studentExam.isSubmitted(), studentExam.getWorkingTime(), studentExam.isStarted(), studentExam.getStartedDate(),
                studentExam.getSubmissionDate(), studentExam.isTestRun(), studentExam.isEnded(), studentExam.getUser(), studentExam.getExam(), exerciseDTOs,
                studentExam.getExamSessions());
    }

    private static Object mapExercise(Exercise exercise, boolean includeSolutions) {
        if (exercise instanceof QuizExercise quizExercise) {
            return QuizExerciseForStudentExamDTO.of(quizExercise, includeSolutions);
        }
        return exercise;
    }
}
