package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.List;

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
public class PlagiarismComparison<E extends PlagiarismSubmissionElement> {

    /**
     * First submission involved in this comparison.
     */
    private PlagiarismSubmission<E> submissionA;

    /**
     * Second submission involved in this comparison.
     */
    private PlagiarismSubmission<E> submissionB;

    /**
     * List of matches between both submissions involved in this comparison.
     */
    private List<PlagiarismMatch> matches;

    /**
     * Similarity of the compared submissions (between 0 and 1).
     */
    private int similarity;

    /**
     * Status of this submission comparison.
     */
    private PlagiarismStatus status;

    public PlagiarismSubmission<E> getSubmissionA() {
        return submissionA;
    }

    public void setSubmissionA(PlagiarismSubmission<E> submissionA) {
        this.submissionA = submissionA;
    }

    public PlagiarismSubmission<E> getSubmissionB() {
        return submissionB;
    }

    public void setSubmissionB(PlagiarismSubmission<E> submissionB) {
        this.submissionB = submissionB;
    }

    public List<PlagiarismMatch> getMatches() {
        return matches;
    }

    public void setMatches(List<PlagiarismMatch> matches) {
        this.matches = matches;
    }

    public int getSimilarity() {
        return similarity;
    }

    public void setSimilarity(int similarity) {
        this.similarity = similarity;
    }

    public PlagiarismStatus getStatus() {
        return status;
    }

    public void setStatus(PlagiarismStatus status) {
        this.status = status;
    }
}
