package de.tum.cit.aet.artemis.quiz.dto.participation;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
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
        Set<Submission> submissions = studentParticipation.getSubmissions();
        if (!(participationExercise instanceof QuizExercise quizExercise)) {
            // Return null if the exercise is not a QuizExercise
            return null;
        }
        submissions = submissions.stream().filter(submission -> submission instanceof QuizSubmission).collect(Collectors.toSet());

        Set<QuizSubmissionBeforeEvaluationDTO> submissionsBeforeEvaluation = submissions.stream().map(submission -> (QuizSubmission) submission)
                .map(QuizSubmissionBeforeEvaluationDTO::of).collect(Collectors.toSet());

        return new StudentQuizParticipationWithoutQuestionsDTO(StudentQuizParticipationBaseDTO.of(studentParticipation), QuizExerciseWithoutQuestionsDTO.of(quizExercise),
                submissionsBeforeEvaluation);
    }

}
