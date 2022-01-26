package de.tum.in.www1.artemis.domain.plagiarism;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.jplag.JPlagComparison;
import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
@Entity
@Table(name = "plagiarism_comparison")
public class PlagiarismComparison<E extends PlagiarismSubmissionElement> extends AbstractAuditingEntity implements Comparable<PlagiarismComparison<E>> {

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
     * </p>
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
     * Statement made by student A on the case
     */
    @Column(name = "student_statement_a")
    private String studentStatementA;

    /**
     * Statement made by student B on the case
     */
    @Column(name = "student_statement_b")
    private String studentStatementB;

    /**
     * Status on the Statement student A made
     */
    @Column(name = "status_a")
    private PlagiarismStatus statusA;

    /**
     * Status on the Statement student B made
     */
    @Column(name = "status_b")
    private PlagiarismStatus statusB;

    /**
     * Instructor statement/message sent to student A, null if not sent
     */
    @Nullable
    @Column(name = "instructor_statement_a")
    private String instructorStatementA;

    /**
     * Instructor statement/message sent to student B, null if not sent
     */
    @Nullable
    @Column(name = "instructor_statement_b")
    private String instructorStatementB;

    /**
     * Timestamp when instructor set final status for student A
     */
    @Column(name = "status_a_date")
    private Instant statusADate;

    /**
     * Timestamp when instructor set final status for student B
     */
    @Column(name = "status_b_date")
    private Instant statusBDate;

    /**
     * Timestamp when student A made statement on the case
     */
    @Column(name = "student_statement_a_date")
    private Instant studentStatementADate;

    /**
     * Timestamp when student B made statement on the case
     */
    @Column(name = "student_statement_b_date")
    private Instant studentStatementBDate;

    /**
     * Timestamp when instructor statement/message sent to student A
     */
    @Column(name = "instructor_statement_a_date")
    private Instant instructorStatementADate;

    /**
     * Timestamp when instructor statement/message sent to student B
     */
    @Column(name = "instructor_statement_b_date")
    private Instant instructorStatementBDate;

    /**
     * Create a new PlagiarismComparison instance from an existing JPlagComparison object.
     *
     * @param jplagComparison JPlag comparison to map to the new PlagiarismComparison instance
     * @return a new instance with the content of the JPlagComparison
     */
    public static PlagiarismComparison<TextSubmissionElement> fromJPlagComparison(JPlagComparison jplagComparison) {
        PlagiarismComparison<TextSubmissionElement> comparison = new PlagiarismComparison<>();

        comparison.setSubmissionA(PlagiarismSubmission.fromJPlagSubmission(jplagComparison.getFirstSubmission()));
        comparison.setSubmissionB(PlagiarismSubmission.fromJPlagSubmission(jplagComparison.getSecondSubmission()));
        comparison.setMatches(jplagComparison.getMatches().stream().map(PlagiarismMatch::fromJPlagMatch).collect(Collectors.toSet()));
        comparison.setSimilarity(jplagComparison.similarity());
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

    public String getStudentStatementA() {
        return studentStatementA;
    }

    public void setStudentStatementA(String studentStatementA) {
        this.studentStatementA = studentStatementA;
    }

    public String getStudentStatementB() {
        return studentStatementB;
    }

    public void setStudentStatementB(String studentStatementB) {
        this.studentStatementB = studentStatementB;
    }

    public PlagiarismStatus getStatusA() {
        return statusA;
    }

    public void setStatusA(PlagiarismStatus statusA) {
        this.statusA = statusA;
    }

    public PlagiarismStatus getStatusB() {
        return statusB;
    }

    public void setStatusB(PlagiarismStatus statusB) {
        this.statusB = statusB;
    }

    public String getInstructorStatementA() {
        return instructorStatementA;
    }

    public void setInstructorStatementA(String instructorStatementA) {
        this.instructorStatementA = instructorStatementA;
    }

    public String getInstructorStatementB() {
        return instructorStatementB;
    }

    public void setInstructorStatementB(String instructorStatementB) {
        this.instructorStatementB = instructorStatementB;
    }

    public Instant getStatusADate() {
        return statusADate;
    }

    public void setStatusADate(Instant finalStatusADate) {
        this.statusADate = finalStatusADate;
    }

    public Instant getStatusBDate() {
        return statusBDate;
    }

    public void setStatusBDate(Instant finalStatusBDate) {
        this.statusBDate = finalStatusBDate;
    }

    public Instant getInstructorStatementADate() {
        return instructorStatementADate;
    }

    public void setInstructorStatementADate(Instant instructorStatementADate) {
        this.instructorStatementADate = instructorStatementADate;
    }

    public Instant getInstructorStatementBDate() {
        return instructorStatementBDate;
    }

    public void setInstructorStatementBDate(Instant instructorStatementBDate) {
        this.instructorStatementBDate = instructorStatementBDate;
    }

    public Instant getStudentStatementADate() {
        return studentStatementADate;
    }

    public void setStudentStatementADate(Instant studentStatementADate) {
        this.studentStatementADate = studentStatementADate;
    }

    public Instant getStudentStatementBDate() {
        return studentStatementBDate;
    }

    public void setStudentStatementBDate(Instant studentStatementBDate) {
        this.studentStatementBDate = studentStatementBDate;
    }

    @Override
    public int compareTo(@NotNull PlagiarismComparison<E> otherComparison) {
        return Double.compare(similarity, otherComparison.similarity);
    }

    @Override
    public String toString() {
        return "PlagiarismComparison{" + "similarity=" + similarity + ", status=" + status + ", studentStatementA='" + studentStatementA + '\'' + ", studentStatementB='"
                + studentStatementB + '\'' + ", statusA=" + statusA + ", statusB=" + statusB + ", instructorStatementA='" + instructorStatementA + '\'' + ", instructorStatementB='"
                + instructorStatementB + '\'' + ", finalStatusADate='" + statusADate + '\'' + ", finalStatusBDate='" + statusBDate + '\'' + ", studentStatementADate='"
                + studentStatementADate + '\'' + ", studentStatementBDate='" + studentStatementBDate + '\'' + ", instructorStatementADate='" + instructorStatementADate + '\''
                + ", instructorStatementBDate='" + instructorStatementBDate + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        PlagiarismComparison<?> that = (PlagiarismComparison<?>) o;
        return Double.compare(that.similarity, similarity) == 0 && Objects.equals(plagiarismResult, that.plagiarismResult) && Objects.equals(submissionA, that.submissionA)
                && Objects.equals(submissionB, that.submissionB) && Objects.equals(matches, that.matches) && status == that.status
                && Objects.equals(studentStatementA, that.studentStatementA) && Objects.equals(instructorStatementA, that.instructorStatementA)
                && Objects.equals(instructorStatementB, that.instructorStatementB) && Objects.equals(studentStatementB, that.studentStatementB) && statusA == that.statusA
                && statusB == that.statusB && Objects.equals(statusADate, that.statusADate) && Objects.equals(studentStatementADate, that.studentStatementADate)
                && Objects.equals(instructorStatementADate, that.instructorStatementADate) && Objects.equals(statusBDate, that.statusBDate)
                && Objects.equals(instructorStatementBDate, that.instructorStatementBDate) && Objects.equals(studentStatementBDate, that.studentStatementBDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getSubmissionA(), getSubmissionB(), getSimilarity(), getStatus());
    }
}
