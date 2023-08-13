package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.Objects;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
@Entity
@Table(name = "plagiarism_comparison")
public class PlagiarismComparison<E extends PlagiarismSubmissionElement> extends DomainObject implements Comparable<PlagiarismComparison<E>> {

    /**
     * The result this comparison belongs to.
     */
    @ManyToOne(targetEntity = PlagiarismResult.class)
    private PlagiarismResult<E> plagiarismResult;

    /**
     * First submission compared. We maintain a bidirectional relationship manually with #PlagiarismSubmission.plagiarismComparison.
     * <p>
     * Using `CascadeType.ALL` here is fine because we'll never delete a single comparison alone,
     * which would leave empty references from other plagiarism comparisons. Comparisons are
     * always deleted all at once, so we can also cascade deletion.
     */
    @JsonIgnoreProperties("plagiarismComparison")
    @ManyToOne(targetEntity = PlagiarismSubmission.class, cascade = CascadeType.ALL)
    @JoinColumn(name = "submission_a_id")
    private PlagiarismSubmission<E> submissionA;

    /**
     * Second submission compared. We maintain a bidirectional relationship manually with #PlagiarismSubmission.plagiarismComparison.
     * <p>
     * Using `CascadeType.ALL` here is fine because we'll never delete a single comparison alone,
     * which would leave empty references from other plagiarism comparisons. Comparisons are
     * always deleted all at once, so we can also cascade deletion.
     */
    @JsonIgnoreProperties("plagiarismComparison")
    @ManyToOne(targetEntity = PlagiarismSubmission.class, cascade = CascadeType.ALL)
    @JoinColumn(name = "submission_b_id")
    private PlagiarismSubmission<E> submissionB;

    /**
     * List of matches between both submissions involved in this comparison.
     */
    @CollectionTable(name = "plagiarism_comparison_matches", joinColumns = @JoinColumn(name = "plagiarism_comparison_id"))
    @ElementCollection(fetch = FetchType.EAGER)
    @JoinColumn(name = "plagiarism_comparison_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    protected Set<PlagiarismMatch> matches;

    /**
     * Similarity of the compared submissions in percentage (between 0 and 100).
     */
    @Column(name = "similarity")
    private double similarity;

    /**
     * Status of this submission comparison.
     */
    @Column(name = "status")
    private PlagiarismStatus status = PlagiarismStatus.NONE;

    /**
     * Maintain the bidirectional relationship manually
     *
     * @param submissionA the new submission which will be attached to the comparison
     */
    public void setSubmissionA(PlagiarismSubmission<?> submissionA) {
        this.submissionA = (PlagiarismSubmission<E>) submissionA;
        if (this.submissionA != null) {
            // Important: make sure to maintain the custom bidirectional association
            this.submissionA.setPlagiarismComparison(this);
        }
    }

    /**
     * Maintain the bidirectional relationship manually
     *
     * @param submissionB the new submission which will be attached to the comparison
     */
    public void setSubmissionB(PlagiarismSubmission<?> submissionB) {
        this.submissionB = (PlagiarismSubmission<E>) submissionB;
        if (this.submissionB != null) {
            // Important: make sure to maintain the custom bidirectional association
            this.submissionB.setPlagiarismComparison(this);
        }
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

    @Override
    public int compareTo(@NotNull PlagiarismComparison<E> otherComparison) {
        return Double.compare(similarity, otherComparison.similarity);
    }

    @Override
    public String toString() {
        return "PlagiarismComparison{" + "similarity=" + similarity + ", status=" + status + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }

        PlagiarismComparison<?> that = (PlagiarismComparison<?>) obj;
        return Double.compare(that.similarity, similarity) == 0 && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getSimilarity(), getStatus());
    }
}
