package de.tum.in.www1.artemis.service.plagiarism.text;

import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;

@Service
public class TextPlagiarismDetectionService {

    /**
     * Convenience method to extract all latest submissions from a TextExercise and compute pair-wise distances.
     *
     * @param exerciseWithParticipationsAndSubmissions Text Exercise with fetched participations and submissions
     * @param comparisonStrategy TextComparisonStrategy
     * @return Map of text submission pairs and distance score
     */
    public Map<Set<TextSubmission>, Double> compareSubmissionsForExerciseWithStrategy(TextExercise exerciseWithParticipationsAndSubmissions,
            TextComparisonStrategy comparisonStrategy) {
        final List<TextSubmission> textSubmissions = textSubmissionsForComparison(exerciseWithParticipationsAndSubmissions);
        return compareSubmissionsForExerciseWithStrategy(textSubmissions, comparisonStrategy);
    }

    /**
     * Pairwise comparison of text submissions using a TextComparisonStrategy
     *
     * @param textSubmissions List of text submissions
     * @param comparisonStrategy TextComparisonStrategy
     * @return Map of text submission pairs and distance score
     */
    public Map<Set<TextSubmission>, Double> compareSubmissionsForExerciseWithStrategy(List<TextSubmission> textSubmissions, TextComparisonStrategy comparisonStrategy) {
        final Map<Set<TextSubmission>, Double> map = new HashMap<>();

        // it is intended to use the classic for loop here, because we only want to check similarity between two different submissions once
        for (int i = 0; i < textSubmissions.size(); i++) {
            for (int j = i + 1; j < textSubmissions.size(); j++) {
                final TextSubmission a = textSubmissions.get(i);
                final TextSubmission b = textSubmissions.get(j);
                final double similarity = comparisonStrategy.compare(a, b);
                map.put(Set.of(a, b), similarity);
            }
        }

        return map;
    }

    /**
     * Reduce a TextExercise Object to a list of latest text submissions.
     *
     * @param exerciseWithParticipationsAndSubmissions TextExercise with fetched participations and ssubmissions
     * @return List containing the latest text submission for every participation
     */
    public List<TextSubmission> textSubmissionsForComparison(TextExercise exerciseWithParticipationsAndSubmissions) {
        return exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().map(Participation::findLatestSubmission).filter(Optional::isPresent)
                .map(Optional::get).filter(submission -> submission instanceof TextSubmission).map(submission -> (TextSubmission) submission).filter(ts -> !ts.isEmpty())
                .collect(toUnmodifiableList());
    }

}
