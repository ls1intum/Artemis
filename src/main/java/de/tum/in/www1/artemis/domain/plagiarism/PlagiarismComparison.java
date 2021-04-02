package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.*;

import jplag.JPlagComparison;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
    @JsonIgnore
    @ManyToOne(targetEntity = PlagiarismResult.class)
    private PlagiarismResult<E> plagiarismResult;

    /**
     * First submission compared.
     * <p>
     * Using `CascadeType.ALL` here is fine because we'll never delete a single comparison alone,
     * which would leave empty references from other plagiarism comparisons. Comparisons are
     * always deleted all at once, so we can also cascade deletion.
     */
    @ManyToOne(targetEntity = PlagiarismSubmission.class, cascade = CascadeType.ALL)
    @JoinColumn(name = "submission_a_id")
    private PlagiarismSubmission<E> submissionA;

    /**
     * Second submission compared.
     */
    @ManyToOne(targetEntity = PlagiarismSubmission.class, cascade = CascadeType.ALL)
    @JoinColumn(name = "submission_b_id")
    private PlagiarismSubmission<E> submissionB;

    /**
     * List of matches between both submissions involved in this comparison.
     */
    @CollectionTable(name = "plagiarism_comparison_matches", joinColumns = @JoinColumn(name = "plagiarism_comparison_id"))
    @ElementCollection(fetch = FetchType.EAGER)
    protected Set<PlagiarismMatch> matches;

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
        comparison.setMatches(jplagComparison.matches.stream().map(PlagiarismMatch::fromJPlagMatch).collect(Collectors.toSet()));
        comparison.setSimilarity(jplagComparison.percent());
        comparison.setStatus(PlagiarismStatus.NONE);

        return comparison;
    }

    public void setSubmissionA(PlagiarismSubmission<E> submissionA) {
        this.submissionA = submissionA;
    }

    public void setSubmissionB(PlagiarismSubmission<E> submissionB) {
        this.submissionB = submissionB;
    }

    public PlagiarismSubmission<E> getSubmissionA() {
        return submissionA;
    }

    public PlagiarismSubmission<E> getSubmissionB() {
        return this.submissionB;
    }

    public PlagiarismResult<E> getPlagiarismResult() {
        return plagiarismResult;
    }

    public void setPlagiarismResult(PlagiarismResult<E> plagiarismResult) {
        this.plagiarismResult = plagiarismResult;
    }

    public Set<PlagiarismMatch> getMatches() {
        return matches;
    }

    public void setMatches(Set<PlagiarismMatch> matches) {
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
