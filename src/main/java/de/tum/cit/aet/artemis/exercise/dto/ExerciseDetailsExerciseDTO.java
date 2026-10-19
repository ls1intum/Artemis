package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchWithPasswordDTO;

/**
 * The exercise of the student exercise details page: the exercise as the other exercise pages read it, plus what the
 * details route adds for the requesting user.
 * <p>
 * {@code exercise} is unwrapped, so the wire carries its components at the top level, exactly where the entity put
 * them.
 *
 * @param exercise              the exercise, already filtered for the requesting user
 * @param studentAssignedTeamId the team the requesting user belongs to, for team exercises
 * @param submissionPolicy      the submission policy, for programming exercises
 * @param quizBatches           the batch of the requesting user, for quiz exercises
 * @param studentParticipations the participations of the requesting user with their submissions and visible results
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsExerciseDTO(@JsonUnwrapped ExerciseResponseDTO exercise, @Nullable Long studentAssignedTeamId, @Nullable SubmissionPolicyDTO submissionPolicy,
        @Nullable List<QuizBatchWithPasswordDTO> quizBatches, @Nullable List<DetailedParticipationDTO> studentParticipations) {

    /**
     * Maps an exercise the details route has loaded, filtered and attached the participations of the requesting user to.
     *
     * @param exercise the exercise to map
     * @return the exercise as the details page reads it
     */
    public static ExerciseDetailsExerciseDTO of(Exercise exercise) {
        SubmissionPolicyDTO submissionPolicy = null;
        if (exercise instanceof ProgrammingExercise programmingExercise && Hibernate.isInitialized(programmingExercise.getSubmissionPolicy())) {
            submissionPolicy = SubmissionPolicyDTO.of(programmingExercise.getSubmissionPolicy());
        }
        List<QuizBatchWithPasswordDTO> quizBatches = null;
        if (exercise instanceof QuizExercise quizExercise) {
            Set<QuizBatch> batches = quizExercise.getQuizBatches();
            quizBatches = batches != null && Hibernate.isInitialized(batches) ? batches.stream().map(QuizBatchWithPasswordDTO::of).toList() : null;
        }
        Set<StudentParticipation> participations = exercise.getStudentParticipations();
        List<DetailedParticipationDTO> studentParticipations = participations != null && Hibernate.isInitialized(participations)
                ? participations.stream().map(DetailedParticipationDTO::withSubmissions).toList()
                : null;
        return new ExerciseDetailsExerciseDTO(ExerciseResponseDTO.of(exercise), exercise.getStudentAssignedTeamId(), submissionPolicy, quizBatches, studentParticipations);
    }
}
