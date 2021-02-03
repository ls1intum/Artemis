package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import jplag.JPlagComparison;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
@Entity
@Table(name = "plagiarism_comparison")
public class PlagiarismComparison<E extends PlagiarismSubmissionElement> extends DomainObject {

    /**
     * The result this comparison belongs to.
     */
    @ManyToOne(targetEntity = PlagiarismResult.class)
    private PlagiarismResult<E> result;

    /**
     * First submission involved in this comparison.
     */
    @Transient
    private PlagiarismSubmission<E> submissionA;

    /**
     * Second submission involved in this comparison.
     */
    @Transient
    private PlagiarismSubmission<E> submissionB;

    /**
     * List of matches between both submissions involved in this comparison.
     */
    @Transient
    private List<PlagiarismMatch> matches;

    /**
     * Similarity of the compared submissions (between 0 and 1).
     */
    private double similarity;

    /**
     * Status of this submission comparison.
     */
    private PlagiarismStatus status = PlagiarismStatus.NONE;

    /**
     * Create a new PlagiarismComparison instance from an existing JPlagComparison object.
     *
     * @param jplagComparison JPlag comparison to map to the new PlagiarismComparison instance
     * @return a new instance with the content of the JPlagComparison
     */
    public static PlagiarismComparison<TextSubmissionElement> fromJPlagComparison(JPlagComparison jplagComparison) {
        PlagiarismComparison<TextSubmissionElement> comparison = new PlagiarismComparison<>();

        comparison.setSubmissionA(PlagiarismSubmission.fromJPlagSubmission(jplagComparison.subA));
        comparison.setSubmissionB(PlagiarismSubmission.fromJPlagSubmission(jplagComparison.subB));
        comparison.setMatches(jplagComparison.matches.stream().map(PlagiarismMatch::fromJPlagMatch).collect(Collectors.toList()));
        comparison.setSimilarity(jplagComparison.percent());
        comparison.setStatus(PlagiarismStatus.NONE);

        return comparison;
    }

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

    public PlagiarismResult<E> getResult() {
        return result;
    }

    public void setResult(PlagiarismResult<E> result) {
        this.result = result;
    }

    public List<PlagiarismMatch> getMatches() {
        return matches;
    }

    public void setMatches(List<PlagiarismMatch> matches) {
        this.matches = matches;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public PlagiarismStatus getStatus() {
        return status;
    }

    public void setStatus(PlagiarismStatus status) {
        this.status = status;
    }
}
