package de.tum.cit.aet.artemis.plagiarism.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import de.jplag.Match;

/**
 * A {@code PlagiarismMatch} is a sequence of identical elements of both submissions.
 */
@Embeddable
public record PlagiarismMatch(

        // Index of the first element of submission A that is part of this match.
        @Column(name = "start_a") int startA,

        // Index of the first element of submission B that is part of this match.
        @Column(name = "start_b") int startB,

        // Length of the sequence of identical elements.
        int length) {

    /**
     * Create a new {@code PlagiarismMatch} instance from an existing JPlag Match.
     */
    public static PlagiarismMatch fromJPlagMatch(Match jplagMatch) {
        return new PlagiarismMatch(jplagMatch.startOfFirst(), jplagMatch.startOfSecond(), jplagMatch.minimumLength());
    }
}
