package de.tum.in.www1.artemis.domain.plagiarism;

import javax.persistence.Column;
import javax.persistence.Embeddable;

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

    @Override
    public String toString() {
        return "PlagiarismMatch{" + "startA=" + startA + ", startB=" + startB + ", length=" + length + '}';
    }
}
