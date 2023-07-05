package de.tum.in.www1.artemis.service;

import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;

@Service
@Profile("athena")
public class TextAssessmentQueueService {

    private final TextSubmissionRepository textSubmissionRepository;

    public TextAssessmentQueueService(TextSubmissionRepository textSubmissionRepository) {
        this.textSubmissionRepository = textSubmissionRepository;
    }

    /**
     * Calculates the proposedTextSubmission for a given Text exercise
     *
     * @param textExercise the exercise for
     * @return a TextSubmission with the highest information Gain if there is one
     * @throws IllegalArgumentException if textExercise isn't automatically assessable
     */
    @Transactional(readOnly = true) // TODO: remove transactional
    public Optional<TextSubmission> getProposedTextSubmission(TextExercise textExercise) {
        if (!textExercise.isAutomaticAssessmentEnabled()) {
            throw new IllegalArgumentException("The TextExercise is not automatic assessable");
        }
        List<TextSubmission> textSubmissionList = getAllOpenTextSubmissions(textExercise);
        if (textSubmissionList.isEmpty()) {
            return Optional.empty();
        }
        // TODO: request from Athena service
        return Optional.empty();
    }

    /**
     * Return all TextSubmission which are the latest TextSubmission of a Participation and doesn't have a Result so far
     * The corresponding TextBlocks and Participations are retrieved from the database
     *
     * @param exercise Exercise for which all assessed submissions should be retrieved
     * @return an unmodifiable list of all TextSubmission which aren't assessed at the Moment, but need assessment in the future.
     */
    public List<TextSubmission> getAllOpenTextSubmissions(TextExercise exercise) {
        final List<TextSubmission> submissions = textSubmissionRepository.findByParticipation_ExerciseIdAndResultsIsNullAndSubmittedIsTrue(exercise.getId());
        return submissions.stream()
                .filter(submission -> submission.getParticipation().findLatestSubmission().isPresent() && submission == submission.getParticipation().findLatestSubmission().get())
                .toList();
    }
}
