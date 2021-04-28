package de.tum.in.www1.artemis.domain.plagiarism.text;

import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import jplag.JPlagResult;

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
@Entity
public class TextPlagiarismResult extends PlagiarismResult<TextSubmissionElement> {

    public void setJPlagResult(JPlagResult result) {
        for (var jPlagComparison : result.getComparisons()) {
            var comparison = PlagiarismComparison.fromJPlagComparison(jPlagComparison);
            comparison.setPlagiarismResult(this);
            this.comparisons.add(comparison);
        }
        this.duration = result.getDuration();
        this.setSimilarityDistribution(result.getSimilarityDistribution());
    }
}
