package de.tum.cit.aet.artemis.core.service.connectors.athena;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.athena.dto.ExerciseBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.FeedbackBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.ModelingExerciseDTO;
import de.tum.cit.aet.artemis.athena.dto.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ModelingSubmissionDTO;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingSubmissionDTO;
import de.tum.cit.aet.artemis.athena.dto.SubmissionBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.TextExerciseDTO;
import de.tum.cit.aet.artemis.athena.dto.TextFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.TextSubmissionDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

/**
 * Service to convert exercises, submissions and feedback to DTOs for Athena.
 */
@Profile(PROFILE_CORE)
@Service
public class AthenaDTOConverterService {

    @Value("${server.url}")
    private String artemisServerUrl;

    private final TextBlockRepository textBlockRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    public AthenaDTOConverterService(TextBlockRepository textBlockRepository, TextExerciseRepository textExerciseRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, GradingCriterionRepository gradingCriterionRepository) {
        this.textBlockRepository = textBlockRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
    }

    /**
     * Convert an exercise to a DTO for Athena.
     *
     * @param exercise the exercise to convert
     * @return *ExerciseDTO for Athena
     */
    public ExerciseBaseDTO ofExercise(Exercise exercise) {
        switch (exercise.getExerciseType()) {
            case TEXT -> {
                // Fetch text exercise with grade criteria
                var textExercise = textExerciseRepository.findWithGradingCriteriaByIdElseThrow(exercise.getId());
                return TextExerciseDTO.of(textExercise);
            }
            case PROGRAMMING -> {
                // Fetch programming exercise with grading criteria
                var programmingExercise = programmingExerciseRepository.findByIdWithGradingCriteriaElseThrow(exercise.getId());
                return ProgrammingExerciseDTO.of(programmingExercise, artemisServerUrl);
            }
            case MODELING -> {
                // Fetch grading criteria for modeling exercise
                var gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
                exercise.setGradingCriteria(gradingCriteria);
                return ModelingExerciseDTO.of((ModelingExercise) exercise);
            }
        }
        throw new IllegalArgumentException("Exercise type not supported: " + exercise.getExerciseType());
    }

    /**
     * Convert a submission to a DTO for Athena.
     *
     * @param exerciseId the id of the exercise the submission belongs to
     * @param submission the submission to convert
     * @return *SubmissionDTO for Athena
     */
    public SubmissionBaseDTO ofSubmission(long exerciseId, Submission submission) {
        if (submission instanceof TextSubmission textSubmission) {
            return TextSubmissionDTO.of(exerciseId, textSubmission);
        }
        else if (submission instanceof ProgrammingSubmission programmingSubmission) {
            return ProgrammingSubmissionDTO.of(exerciseId, programmingSubmission, artemisServerUrl);
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            return ModelingSubmissionDTO.of(exerciseId, modelingSubmission);
        }
        throw new IllegalArgumentException("Submission type not supported: " + submission.getType());
    }

    /**
     * Convert a feedback to a DTO for Athena.
     *
     * @param exercise     the exercise the feedback belongs to
     * @param submissionId the id of the submission the feedback belongs to
     * @param feedback     the feedback to convert
     * @return *FeedbackDTO for Athena
     */
    public FeedbackBaseDTO ofFeedback(Exercise exercise, long submissionId, Feedback feedback) {
        switch (exercise.getExerciseType()) {
            case TEXT -> {
                TextBlock feedbackTextBlock = null;
                if (feedback.getReference() != null) {
                    feedbackTextBlock = textBlockRepository.findById(feedback.getReference()).orElse(null);
                }
                return TextFeedbackDTO.of(exercise.getId(), submissionId, feedback, feedbackTextBlock);
            }
            case PROGRAMMING -> {
                return ProgrammingFeedbackDTO.of(exercise.getId(), submissionId, feedback);
            }
            case MODELING -> {
                return ModelingFeedbackDTO.of(exercise.getId(), submissionId, feedback);
            }
        }
        throw new IllegalArgumentException("Feedback type not supported: " + exercise.getId());
    }
}
