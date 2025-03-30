package de.tum.cit.aet.artemis.quiz.dto.participation;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionBeforeEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentQuizParticipationWithoutQuestionsDTO(@JsonUnwrapped StudentQuizParticipationBaseDTO studentQuizParticipationBaseDTO, QuizExerciseWithoutQuestionsDTO exercise,
        Set<QuizSubmissionBeforeEvaluationDTO> submissions) implements StudentQuizParticipationDTO {

    /**
     * Creates a StudentQuizParticipationWithoutQuestionsDTO object from a StudentParticipation object.
     *
     * @param studentParticipation the StudentParticipation object
     * @return the created StudentQuizParticipationWithoutQuestionsDTO object
     */
    public static StudentQuizParticipationWithoutQuestionsDTO of(final StudentParticipation studentParticipation) {
        Exercise participationExercise = studentParticipation.getExercise();
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            // TODO: Figure out error handling here
            return null;
        }
        if (!studentParticipation.getSubmissions().stream().allMatch(submission -> submission instanceof QuizSubmission)) {
            // TODO: Handle error if one or more submissions are not QuizSubmissions
            return null;
        }
        Set<QuizSubmissionBeforeEvaluationDTO> submissions = studentParticipation.getSubmissions().stream().map(submission -> (QuizSubmission) submission)
                .map(QuizSubmissionBeforeEvaluationDTO::of).collect(Collectors.toSet());

        // TODO: Results is deprecated, will be removed after QuizView is removed
        return new StudentQuizParticipationWithoutQuestionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithoutQuestionsDTO.of(quizExercise),
                submissions);
    }

}
