package de.tum.cit.aet.artemis.service.connectors.athena;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Feedback;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.TextBlock;
import de.tum.cit.aet.artemis.domain.TextSubmission;
import de.tum.cit.aet.artemis.domain.modeling.ModelingExercise;
import de.tum.cit.aet.artemis.domain.modeling.ModelingSubmission;
import de.tum.cit.aet.artemis.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.service.dto.athena.ExerciseBaseDTO;
import de.tum.cit.aet.artemis.service.dto.athena.FeedbackBaseDTO;
import de.tum.cit.aet.artemis.service.dto.athena.ModelingExerciseDTO;
import de.tum.cit.aet.artemis.service.dto.athena.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.service.dto.athena.ModelingSubmissionDTO;
import de.tum.cit.aet.artemis.service.dto.athena.ProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.service.dto.athena.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.service.dto.athena.ProgrammingSubmissionDTO;
import de.tum.cit.aet.artemis.service.dto.athena.SubmissionBaseDTO;
import de.tum.cit.aet.artemis.service.dto.athena.TextExerciseDTO;
import de.tum.cit.aet.artemis.service.dto.athena.TextFeedbackDTO;
import de.tum.cit.aet.artemis.service.dto.athena.TextSubmissionDTO;

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
