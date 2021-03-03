package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

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
    @ManyToOne(targetEntity = PlagiarismResult.class)
    @JsonIgnore
    private PlagiarismResult<E> plagiarismResult;

    /**
     * List of the two submissions compared.
     */
    @ManyToMany(targetEntity = PlagiarismSubmission.class, cascade = CascadeType.ALL)
    private List<PlagiarismSubmission<E>> submissions = new ArrayList<>();

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

        comparison.addSubmission(PlagiarismSubmission.fromJPlagSubmission(jplagComparison.subA));
        comparison.addSubmission(PlagiarismSubmission.fromJPlagSubmission(jplagComparison.subB));
        comparison.setMatches(jplagComparison.matches.stream().map(PlagiarismMatch::fromJPlagMatch).collect(Collectors.toList()));
        comparison.setSimilarity(jplagComparison.percent());
        comparison.setStatus(PlagiarismStatus.NONE);

        return comparison;
    }

    public void addSubmission(PlagiarismSubmission<E> submission) {
        this.submissions.add(submission);
    }

    public PlagiarismSubmission<E> getSubmissionA() {
        return this.submissions.get(0);
    }

    public PlagiarismSubmission<E> getSubmissionB() {
        return this.submissions.get(1);
    }

    public PlagiarismResult<E> getPlagiarismResult() {
        return plagiarismResult;
    }

    public void setPlagiarismResult(PlagiarismResult<E> plagiarismResult) {
        this.plagiarismResult = plagiarismResult;
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
