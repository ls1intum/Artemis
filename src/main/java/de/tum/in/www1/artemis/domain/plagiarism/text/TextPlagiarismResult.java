package de.tum.in.www1.artemis.domain.plagiarism.text;

import java.util.stream.Collectors;

import jplag.JPlagResult;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
public class TextPlagiarismResult extends PlagiarismResult<TextSubmissionElement> {

    /**
     * Empty constructor required.
     */
    public TextPlagiarismResult() {
        // Intentionally left empty.
    }

    public TextPlagiarismResult(JPlagResult result) {
        this.duration = result.getDuration();
        this.similarityDistribution = result.getSimilarityDistribution();

        this.comparisons = result.getComparisons().stream().map(PlagiarismComparison::fromJPlagComparison).collect(Collectors.toList());
    }

}
