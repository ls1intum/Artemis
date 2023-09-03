package de.tum.in.www1.artemis.service.connectors.athena;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.service.dto.athena.*;

/**
 * Service to convert exercises, submissions and feedback to DTOs for Athena.
 */
@Service
public class AthenaDTOConverter {

    @Value("${server.url}")
    private String artemisServerUrl;

    private final TextBlockRepository textBlockRepository;

    public AthenaDTOConverter(TextBlockRepository textBlockRepository) {
        this.textBlockRepository = textBlockRepository;
    }

    /**
     * Convert an exercise to a DTO for Athena.
     *
     * @param exercise the exercise to convert
     * @return *ExerciseDTO for Athena
     */
    public Object ofExercise(Exercise exercise) {
        switch (exercise.getExerciseType()) {
            case TEXT -> {
                return TextExerciseDTO.of((TextExercise) exercise);
            }
            case PROGRAMMING -> {
                return ProgrammingExerciseDTO.of((ProgrammingExercise) exercise, artemisServerUrl);
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
    public Object ofSubmission(long exerciseId, Submission submission) {
        if (submission instanceof TextSubmission) {
            return TextSubmissionDTO.of(exerciseId, (TextSubmission) submission);
        }
        else if (submission instanceof ProgrammingSubmission) {
            return ProgrammingSubmissionDTO.of(exerciseId, (ProgrammingSubmission) submission, artemisServerUrl);
        }
        throw new IllegalArgumentException("Submission type not supported: " + submission.getType());
    }

    /**
     * Convert a feedback to a DTO for Athena.
     *
     * @param exerciseId the id of the exercise the feedback belongs to
     * @param feedback   the feedback to convert
     * @return *FeedbackDTO for Athena
     */
    public Object ofFeedback(long exerciseId, long submissionId, Feedback feedback) {
        var exerciseType = feedback.getResult().getParticipation().getExercise().getExerciseType();
        switch (exerciseType) {
            case TEXT -> {
                TextBlock feedbackTextBlock = null;
                if (feedback.getReference() != null) {
                    feedbackTextBlock = textBlockRepository.findById(feedback.getReference()).orElse(null);
                }
                return TextFeedbackDTO.of(exerciseId, submissionId, feedback, feedbackTextBlock);
            }
            case PROGRAMMING -> {
                return ProgrammingFeedbackDTO.of(exerciseId, submissionId, feedback);
            }
        }
        throw new IllegalArgumentException("Feedback type not supported: " + exerciseType);
    }
}
