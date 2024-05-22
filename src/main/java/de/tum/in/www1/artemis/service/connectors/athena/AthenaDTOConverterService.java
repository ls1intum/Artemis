package de.tum.in.www1.artemis.service.connectors.athena;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.GradingCriterionRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.service.dto.athena.Exercise;
import de.tum.in.www1.artemis.service.dto.athena.Feedback;
import de.tum.in.www1.artemis.service.dto.athena.ModelingExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.ModelingFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.ModelingSubmissionDTO;
import de.tum.in.www1.artemis.service.dto.athena.ProgrammingExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.ProgrammingFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.ProgrammingSubmissionDTO;
import de.tum.in.www1.artemis.service.dto.athena.Submission;
import de.tum.in.www1.artemis.service.dto.athena.TextExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextSubmissionDTO;

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
    public Exercise ofExercise(de.tum.in.www1.artemis.domain.Exercise exercise) {
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
    public Submission ofSubmission(long exerciseId, de.tum.in.www1.artemis.domain.Submission submission) {
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
    public Feedback ofFeedback(de.tum.in.www1.artemis.domain.Exercise exercise, long submissionId, de.tum.in.www1.artemis.domain.Feedback feedback) {
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
