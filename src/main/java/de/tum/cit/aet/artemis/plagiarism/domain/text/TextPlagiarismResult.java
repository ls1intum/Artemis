package de.tum.cit.aet.artemis.plagiarism.domain.text;

import jakarta.persistence.Entity;

import de.jplag.JPlagResult;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
@Entity
public class TextPlagiarismResult extends PlagiarismResult<TextSubmissionElement> {

    private static final int ORIGINAL_SIZE = 100;

    private static final int REDUCED_SIZE = 10;

    /**
     * converts the given JPlagResult into a TextPlagiarismResult, only uses the 500 most interesting comparisons based on the highest similarity
     *
     * @param result   the JPlagResult contains comparisons
     * @param exercise the exercise to which the result should belong, either Text or Programming
     */
    public void convertJPlagResult(JPlagResult result, Exercise exercise) {
        // sort and limit the number of comparisons to 500
        var comparisons = result.getComparisons(500);
        // only convert those 500 comparisons to save memory and cpu power
        for (var jPlagComparison : comparisons) {
            var comparison = PlagiarismComparison.fromJPlagComparison(jPlagComparison, exercise, result.getOptions().submissionDirectories().iterator().next());
            comparison.setPlagiarismResult(this);
            this.comparisons.add(comparison);
        }
        this.duration = result.getDuration();

        // Convert JPlag Similarity Distribution from int[100] to int[10]
        int[] tenthPercentileSimilarityDistribution = new int[REDUCED_SIZE];
        for (int i = 0; i < ORIGINAL_SIZE; i++) {
            tenthPercentileSimilarityDistribution[i / REDUCED_SIZE] += result.getSimilarityDistribution()[i];
        }

        this.setSimilarityDistribution(tenthPercentileSimilarityDistribution);
        this.setExercise(exercise);
    }
}
