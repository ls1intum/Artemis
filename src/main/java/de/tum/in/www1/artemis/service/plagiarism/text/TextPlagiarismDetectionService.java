package de.tum.in.www1.artemis.service.plagiarism.text;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;

@Service
public class TextPlagiarismDetectionService {

    private final Logger log = LoggerFactory.getLogger(TextPlagiarismDetectionService.class);

    /**
     * Convenience method to extract all latest submissions from a TextExercise and compute pair-wise distances.
     *
     * @param exerciseWithParticipationsAndSubmissions Text Exercise with fetched participations and submissions
     * @param comparisonStrategy the chosen comparison strategy
     * @param comparisonStrategyName the name of the strategy for logging purpose
     * @param minimumSimilarity the minimum similarity (between 0 and 1) that should be reported in the response
     * @return Map of text submission pairs and similarity score
     */
    public Map<Set<TextSubmission>, Double> compareSubmissionsForExerciseWithStrategy(TextExercise exerciseWithParticipationsAndSubmissions,
            TextComparisonStrategy comparisonStrategy, String comparisonStrategyName, double minimumSimilarity) {
        final List<TextSubmission> textSubmissions = textSubmissionsForComparison(exerciseWithParticipationsAndSubmissions);
        return compareSubmissionsForExerciseWithStrategy(textSubmissions, comparisonStrategy, comparisonStrategyName, minimumSimilarity);
    }

    /**
     * Pairwise comparison of text submissions using a TextComparisonStrategy
     *
     * @param textSubmissions List of text submissions
     * @param comparisonStrategy the chosen comparison strategy
     * @param comparisonStrategyName the name of the strategy for logging purpose
     * @param minimumSimilarity the minimum similarity (between 0 and 1) that should be reported in the response
     * @return Map of text submission pairs and similarity score
     */
    public Map<Set<TextSubmission>, Double> compareSubmissionsForExerciseWithStrategy(List<TextSubmission> textSubmissions, TextComparisonStrategy comparisonStrategy,
            String comparisonStrategyName, double minimumSimilarity) {
        final Map<Set<TextSubmission>, Double> map = new HashMap<>();

        // it is intended to use the classic for loop here, because we only want to check similarity between two different submissions once
        for (int i = 0; i < textSubmissions.size(); i++) {
            for (int j = i + 1; j < textSubmissions.size(); j++) {
                final TextSubmission textSubmission1 = textSubmissions.get(i);
                final TextSubmission textSubmission2 = textSubmissions.get(j);
                final double similarity = 1 - comparisonStrategy.compare(textSubmission1, textSubmission2);
                log.debug("Compare result " + i + " with " + j + ": " + similarity);
                if (similarity >= minimumSimilarity) {
                    log.info("Found similar text " + i + " with " + j + ": " + similarity + " (using strategy " + comparisonStrategyName + ")");
                    map.put(Set.of(textSubmission1, textSubmission2), similarity);
                }
            }
        }

        log.info("Found " + map.size() + " similar text submission combinations ( > " + minimumSimilarity + ") using strategy " + comparisonStrategyName);

        return map;
    }

    /**
     * Reduce a TextExercise Object to a list of latest text submissions. Filters the empty ones because they do not need to be compared
     *
     * @param exerciseWithParticipationsAndSubmissions TextExercise with fetched participations and ssubmissions
     * @return List containing the latest text submission for every participation
     */
    public List<TextSubmission> textSubmissionsForComparison(TextExercise exerciseWithParticipationsAndSubmissions) {
        var textSubmissions = exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().map(Participation::findLatestSubmission)
                .filter(Optional::isPresent).map(Optional::get).filter(submission -> submission instanceof TextSubmission).map(submission -> (TextSubmission) submission)
                .collect(toList());
        log.info("Found " + textSubmissions.size() + " text submissions in exercise " + exerciseWithParticipationsAndSubmissions.getId());
        return textSubmissions.parallelStream().filter(textSubmission -> !textSubmission.isEmpty()).collect(toUnmodifiableList());
    }

}
