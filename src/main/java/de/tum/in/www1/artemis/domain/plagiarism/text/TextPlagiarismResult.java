package de.tum.in.www1.artemis.domain.plagiarism.text;

import java.util.HashSet;
import java.util.stream.Collectors;

import javax.persistence.Entity;

import jplag.JPlagResult;
import de.tum.in.www1.artemis.domain.plagiarism.*;

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
@Entity
public class TextPlagiarismResult extends PlagiarismResult<TextSubmissionElement> {

    /**
     * Empty constructor required.
     */
    public TextPlagiarismResult() {
        // Intentionally left empty.
        this.comparisons = new HashSet<>();
    }

    public TextPlagiarismResult(JPlagResult result) {
        this.comparisons = result.getComparisons().stream().map(PlagiarismComparison::fromJPlagComparison).peek(comparison -> {
            comparison.setPlagiarismResult(this);
        }).collect(Collectors.toSet());
        this.duration = result.getDuration();

        this.setSimilarityDistribution(result.getSimilarityDistribution());
    }

}
