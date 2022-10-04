package de.tum.in.www1.artemis.domain.plagiarism.text;

import javax.persistence.Entity;

import org.apache.commons.lang3.ArrayUtils;

import de.jplag.JPlagResult;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
@Entity
public class TextPlagiarismResult extends PlagiarismResult<TextSubmissionElement> {

    /**
     * converts the given JPlagResult into a TextPlagiarismResult, only uses the 500 most interesting comparisons based on the highest similarity
     * @param result the JPlagResult contains comparisons
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
        // NOTE: there seems to be an issue in JPlag 4.0 that the similarity distribution is reversed, either in the implementation or in the documentation.
        // we use it like this: 0: [0% - 10%), 1: [10% - 20%), 2: [20% - 30%), ..., 9: [90% - 100%] so we reverse it
        var similarityDistribution = result.getSimilarityDistribution();
        ArrayUtils.reverse(similarityDistribution);
        this.setSimilarityDistribution(similarityDistribution);
        this.setExercise(exercise);
    }
}
