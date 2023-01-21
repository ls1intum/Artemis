package de.tum.in.www1.artemis.domain.plagiarism.text;

import javax.persistence.Entity;

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
        this.setSimilarityDistribution(result.getSimilarityDistribution());
        this.setExercise(exercise);
    }
}
