package de.tum.cit.aet.artemis.plagiarism.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import de.jplag.Match;

/**
 * A `PlagiarismMatch` is a sequence of identical elements of both submissions.
 */
@Embeddable
public class PlagiarismMatch {

    /**
     * Index of the first element of submission A that is part of this match.
     */
    @Column(name = "start_a")
    private int startA;

    /**
     * Index of the first element of submission B that is part of this match.
     */
    @Column(name = "start_b")
    private int startB;

    /**
     * Length of the sequence of identical elements, beginning at startA and startB, respectively.
     */
    private int length;

    /**
     * Create a new PlagiarismMatch instance from an existing JPlag Match
     *
     * @param jplagMatch the JPlag Match to create the PlagiarismMatch from
     * @return a new PlagiarismMatch instance
     */
    public static PlagiarismMatch fromJPlagMatch(Match jplagMatch) {
        PlagiarismMatch match = new PlagiarismMatch();
        match.setStartA(jplagMatch.startOfFirst());
        match.setStartB(jplagMatch.startOfSecond());
        match.setLength(jplagMatch.length());
        return match;
    }

    public int getStartA() {
        return startA;
    }

    public void setStartA(int startA) {
        this.startA = startA;
    }

    public int getStartB() {
        return startB;
    }

    public void setStartB(int startB) {
        this.startB = startB;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    /**
     * Overrides the equals method to ensure proper equality checks to not rely on the default object instance equality.
     *
     * <p>
     * This is important to ensure uniqueness, e.g. when loading objects from the database into a
     * {@link java.util.Set}.
     *
     * <p>
     * Note:
     * This is required here since unlike other domain classes this one does not extend
     * {@link de.tum.cit.aet.artemis.domain.DomainObject} since it only represents an {@link Embeddable} part of another
     * entity.
     * Therefore, it does inherit neither the database ID attribute nor the matching
     * {@link de.tum.cit.aet.artemis.domain.DomainObject#equals(Object)} implementation.
     * Instead, we have to compare all relevant attributes here.
     *
     * @param other Some other object.
     * @return True, if this and the other object are equal.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof PlagiarismMatch that) {
            return startA == that.startA && startB == that.startB && length == that.length;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startA, startB, length);
    }

    @Override
    public String toString() {
        return "PlagiarismMatch{" + "startA=" + startA + ", startB=" + startB + ", length=" + length + '}';
    }
}
