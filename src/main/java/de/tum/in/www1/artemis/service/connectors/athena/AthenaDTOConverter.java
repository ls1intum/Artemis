package de.tum.in.www1.artemis.service.connectors.athena;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.service.dto.athena.*;

/**
 * Service to convert exercises, submissions and feedback to DTOs for Athena.
 */
@Service
public class AthenaDTOConverter {

    @Value("${server.url}")
    private String artemisServerUrl;

    private final TextBlockRepository textBlockRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public AthenaDTOConverter(TextBlockRepository textBlockRepository, TextExerciseRepository textExerciseRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.textBlockRepository = textBlockRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Convert an exercise to a DTO for Athena.
     *
     * @param exercise the exercise to convert
     * @return *ExerciseDTO for Athena
     */
    public ExerciseDTO ofExercise(Exercise exercise) {
        switch (exercise.getExerciseType()) {
            case TEXT -> {
                // Fetch text exercise with grade criteria
                var textExercise = textExerciseRepository.findByIdWithGradingCriteriaElseThrow(exercise.getId());
                return TextExerciseDTO.of(textExercise);
            }
            case PROGRAMMING -> {
                // Fetch programming exercise with grading criteria
                var programmingExercise = programmingExerciseRepository.findByIdWithGradingCriteriaElseThrow(exercise.getId());
                return ProgrammingExerciseDTO.of(programmingExercise, artemisServerUrl);
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
    public SubmissionDTO ofSubmission(long exerciseId, Submission submission) {
        if (submission instanceof TextSubmission textSubmission) {
            return TextSubmissionDTO.of(exerciseId, textSubmission);
        }
        else if (submission instanceof ProgrammingSubmission programmingSubmission) {
            return ProgrammingSubmissionDTO.of(exerciseId, programmingSubmission, artemisServerUrl);
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
    public FeedbackDTO ofFeedback(Exercise exercise, long submissionId, Feedback feedback) {
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
        }
        throw new IllegalArgumentException("Feedback type not supported: " + exercise.getId());
    }
}
