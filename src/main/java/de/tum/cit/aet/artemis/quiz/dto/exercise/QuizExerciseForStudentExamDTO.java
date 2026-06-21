package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupForStudentExamDTO;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentExamQuizParticipationDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseForStudentExamDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise, List<?> quizQuestions, Set<String> categories,
        Set<StudentExamQuizParticipationDTO> studentParticipations, ExerciseGroupForStudentExamDTO exerciseGroup, String problemStatement, String gradingInstructions) {

    /**
     * Creates a quiz exercise response DTO for a student exam response.
     *
     * @param quizExercise     the quiz exercise to map
     * @param includeSolutions whether solution fields should be included in quiz questions and answer options
     * @return the response DTO
     */
    public static QuizExerciseForStudentExamDTO of(QuizExercise quizExercise, boolean includeSolutions) {
        List<?> questionDTOs = includeSolutions ? quizExercise.getQuizQuestions().stream().map(QuizQuestionWithSolutionDTO::of).toList()
                : quizExercise.getQuizQuestions().stream().map(QuizQuestionWithoutSolutionDTO::of).toList();
        return new QuizExerciseForStudentExamDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), questionDTOs, quizExercise.getCategories(),
                mapStudentParticipations(quizExercise, includeSolutions), ExerciseGroupForStudentExamDTO.of(quizExercise.getExerciseGroup()), quizExercise.getProblemStatement(),
                quizExercise.getGradingInstructions());
    }

    private static Set<StudentExamQuizParticipationDTO> mapStudentParticipations(QuizExercise quizExercise, boolean includeSolutions) {
        Set<StudentParticipation> studentParticipations = quizExercise.getStudentParticipations();
        if (studentParticipations == null || !Hibernate.isInitialized(studentParticipations)) {
            return Set.of();
        }
        return studentParticipations.stream().map(studentParticipation -> StudentExamQuizParticipationDTO.of(studentParticipation, includeSolutions)).collect(Collectors.toSet());
    }
}
