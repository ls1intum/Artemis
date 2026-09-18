package de.tum.cit.aet.artemis.plagiarism.domain;

import java.io.File;
import java.util.HashSet;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.jplag.JPlagComparison;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
@Entity
@Table(name = "plagiarism_comparison")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismComparison extends DomainObject implements Comparable<PlagiarismComparison> {

    /**
     * The result this comparison belongs to.
     */
    @ManyToOne(targetEntity = PlagiarismResult.class)
    @JoinColumn(nullable = false)
    @Parent
    private PlagiarismResult plagiarismResult;

    /**
     * The two submissions compared, each naming this comparison and the side it is on.
     * <p>
     * The comparison used to name them instead, through {@code submission_a_id} and {@code submission_b_id}. That put
     * the only pointer to a submission on the other row, so a submission whose comparison stopped naming it was
     * reachable from nowhere, and it made the two tables reference each other, which every deletion had to unpick by
     * clearing one side first.
     * <p>
     * {@code CascadeType.ALL} is fine because a single comparison is never deleted on its own: comparisons are always
     * deleted all at once, so deletion can cascade.
     */
    @JsonIgnoreProperties(value = "plagiarismComparison", allowSetters = true)
    @OneToMany(mappedBy = "plagiarismComparison", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<PlagiarismSubmission> submissions = new HashSet<>();

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
     * Puts a submission on the first side of this comparison, replacing whatever was there.
     *
     * @param submissionA the submission to compare, or null to leave the side empty
     */
    public void setSubmissionA(PlagiarismSubmission submissionA) {
        setSubmission(PlagiarismComparisonSide.FIRST, submissionA);
    }

    /**
     * Puts a submission on the second side of this comparison, replacing whatever was there.
     *
     * @param submissionB the submission to compare, or null to leave the side empty
     */
    public void setSubmissionB(PlagiarismSubmission submissionB) {
        setSubmission(PlagiarismComparisonSide.SECOND, submissionB);
    }

    @JsonIgnore
    public PlagiarismSubmission getSubmissionA() {
        return submissionOf(PlagiarismComparisonSide.FIRST);
    }

    @JsonIgnore
    public PlagiarismSubmission getSubmissionB() {
        return submissionOf(PlagiarismComparisonSide.SECOND);
    }

    public Set<PlagiarismSubmission> getSubmissions() {
        return submissions;
    }

    private void setSubmission(PlagiarismComparisonSide side, PlagiarismSubmission submission) {
        submissions.removeIf(existing -> existing.getSide() == side);
        if (submission != null) {
            submission.setSide(side);
            submission.setPlagiarismComparison(this);
            submissions.add(submission);
        }
    }

    private PlagiarismSubmission submissionOf(PlagiarismComparisonSide side) {
        return submissions.stream().filter(submission -> submission.getSide() == side).findFirst().orElse(null);
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
    public int compareTo(@NonNull PlagiarismComparison otherComparison) {
        return Double.compare(similarity, otherComparison.similarity);
    }

    @Override
    public String toString() {
        return "PlagiarismComparison{" + "similarity=" + similarity + ", status=" + status + '}';
    }
}
