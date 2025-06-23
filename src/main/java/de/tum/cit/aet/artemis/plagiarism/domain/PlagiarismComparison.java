package de.tum.cit.aet.artemis.plagiarism.domain;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.jplag.JPlagComparison;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
@Entity
@Table(name = "plagiarism_comparison")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismComparison extends DomainObject implements Comparable<PlagiarismComparison> {

    /**
     * The result this comparison belongs to.
     */
    @ManyToOne(targetEntity = PlagiarismResult.class)
    private PlagiarismResult plagiarismResult;

    /**
     * First submission compared. We maintain a bidirectional relationship manually with #PlagiarismSubmission.plagiarismComparison.
     * <p>
     * Using `CascadeType.ALL` here is fine because we'll never delete a single comparison alone,
     * which would leave empty references from other plagiarism comparisons. Comparisons are
     * always deleted all at once, so we can also cascade deletion.
     */
    @JsonIgnoreProperties(value = "plagiarismComparison", allowSetters = true)
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "submission_a_id")
    private PlagiarismSubmission submissionA;

    /**
     * Second submission compared. We maintain a bidirectional relationship manually with #PlagiarismSubmission.plagiarismComparison.
     * <p>
     * Using `CascadeType.ALL` here is fine because we'll never delete a single comparison alone,
     * which would leave empty references from other plagiarism comparisons. Comparisons are
     * always deleted all at once, so we can also cascade deletion.
     */
    @JsonIgnoreProperties(value = "plagiarismComparison", allowSetters = true)
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "submission_b_id")
    private PlagiarismSubmission submissionB;

    /**
     * List of matches between both submissions involved in this comparison.
     */
    @CollectionTable(name = "plagiarism_comparison_matches", joinColumns = @JoinColumn(name = "plagiarism_comparison_id"))
    @ElementCollection(fetch = FetchType.EAGER)
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
     * Create a new PlagiarismComparison instance from an existing JPlagComparison object.
     *
     * @param jplagComparison     JPlag comparison to map to the new PlagiarismComparison instance
     * @param exercise            the exercise to which the comparison belongs, either Text or Programming
     * @param submissionDirectory the directory to which all student submissions have been downloaded / stored
     * @return a new instance with the content of the JPlagComparison
     */
    public static PlagiarismComparison fromJPlagComparison(JPlagComparison jplagComparison, Exercise exercise, File submissionDirectory) {
        PlagiarismComparison comparison = new PlagiarismComparison();

        comparison.setSubmissionA(PlagiarismSubmission.fromJPlagSubmission(jplagComparison.firstSubmission(), exercise, submissionDirectory));
        comparison.setSubmissionB(PlagiarismSubmission.fromJPlagSubmission(jplagComparison.secondSubmission(), exercise, submissionDirectory));
        comparison.setMatches(jplagComparison.matches().stream().map(PlagiarismMatch::fromJPlagMatch).collect(Collectors.toSet()));
        // Note: JPlag returns a value between 0 and 1, we assume and store a value between 0 and 100 (percentage) in the database
        comparison.setSimilarity(jplagComparison.similarity() * 100);
        comparison.setStatus(PlagiarismStatus.NONE);

        return comparison;
    }

    /**
     * Maintain the bidirectional relationship manually
     *
     * @param submissionA the new submission which will be attached to the comparison
     */
    public void setSubmissionA(PlagiarismSubmission submissionA) {
        this.submissionA = submissionA;
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
    public void setSubmissionB(PlagiarismSubmission submissionB) {
        this.submissionB = submissionB;
        if (this.submissionB != null) {
            // Important: make sure to maintain the custom bidirectional association
            this.submissionB.setPlagiarismComparison(this);
        }
    }

    public PlagiarismSubmission getSubmissionA() {
        return submissionA;
    }

    public PlagiarismSubmission getSubmissionB() {
        return this.submissionB;
    }

    public PlagiarismResult getPlagiarismResult() {
        return plagiarismResult;
    }

    public void setPlagiarismResult(PlagiarismResult plagiarismResult) {
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
    public int compareTo(@NotNull PlagiarismComparison otherComparison) {
        return Double.compare(similarity, otherComparison.similarity);
    }

    @Override
    public String toString() {
        return "PlagiarismComparison{" + "similarity=" + similarity + ", status=" + status + '}';
    }
}
