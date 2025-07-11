package de.tum.cit.aet.artemis.plagiarism.domain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.jplag.Submission;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Entity
@Table(name = "plagiarism_submission")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismSubmission extends DomainObject {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismSubmission.class);

    /**
     * ID of the wrapped submission object.
     * <p>
     * We don't use a full relation to `Submission` here to decrease the overhead of creating and
     * fetching `PlagiarismSubmission` objects. We get the submissionId for free, as JPlag
     * uses it as an identifier of each submission during comparison. The ID is used in the client
     * to fetch a submission's contents.
     * <p>
     * Note: For programming exercises, this field actually references the participation instead of
     * a single submission of a student, since the participation ID is required to properly fetch the
     * corresponding repository's contents.
     */
    @Column(name = "submission_id")
    private long submissionId;

    /**
     * Login of the student who created the submission.
     */
    @Column(name = "student_login")
    private String studentLogin;

    /**
     * List of elements the related submission consists of.
     */
    @JsonIgnoreProperties(value = "plagiarismSubmission", allowSetters = true)
    @OneToMany(mappedBy = "plagiarismSubmission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PlagiarismSubmissionElement> elements;

    @ManyToOne
    private PlagiarismCase plagiarismCase;

    /**
     * We maintain a bidirectional relationship manually with submissionA and submissionB
     */
    @JsonIgnoreProperties({ "submissionA", "submissionB" })
    @OneToOne
    @JoinColumn(name = "plagiarism_comparison_id")
    private PlagiarismComparison plagiarismComparison;

    /**
     * Size of the related submission.
     * <p>
     * For text and programming submissions, this is the number of words or tokens.
     */
    @Column(name = "size")
    private int size;

    /**
     * Result score of the related submission.
     */
    @Column(name = "score")
    private Double score;

    /**
     * Create a new PlagiarismSubmission instance from an existing JPlag Submission
     *
     * @param jplagSubmission     the JPlag Submission to create the PlagiarismSubmission from
     * @param exercise            the exercise to which the comparison belongs, either Text or Programming
     * @param submissionDirectory the directory to which all student submissions have been downloaded / stored
     * @return a new PlagiarismSubmission instance
     */
    public static PlagiarismSubmission fromJPlagSubmission(Submission jplagSubmission, Exercise exercise, File submissionDirectory) {
        PlagiarismSubmission submission = new PlagiarismSubmission();

        String[] submissionIdAndStudentLogin = jplagSubmission.getName().split("[-.]");

        long submissionId = 0;
        String studentLogin = "unknown";

        if (submissionIdAndStudentLogin.length >= 2) {
            try {
                submissionId = Long.parseLong(submissionIdAndStudentLogin[0]);
            }
            catch (NumberFormatException e) {
                log.error("Invalid submissionId: {}", e.getMessage());
            }

            studentLogin = submissionIdAndStudentLogin[1];
        }

        submission.setStudentLogin(studentLogin);
        submission.setElements(jplagSubmission.getTokenList().stream().filter(Objects::nonNull)
                .map(token -> PlagiarismSubmissionElement.fromJPlagToken(token, submission, exercise, submissionDirectory)).collect(Collectors.toCollection(ArrayList::new)));
        submission.setSubmissionId(submissionId);
        submission.setSize(jplagSubmission.getNumberOfTokens());
        submission.setScore(null); // TODO

        return submission;
    }

    public String getStudentLogin() {
        return studentLogin;
    }

    public void setStudentLogin(String studentLogin) {
        this.studentLogin = studentLogin;
    }

    public List<PlagiarismSubmissionElement> getElements() {
        return elements;
    }

    public void setElements(List<PlagiarismSubmissionElement> elements) {
        this.elements = elements;
    }

    public long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(long submissionId) {
        this.submissionId = submissionId;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public PlagiarismCase getPlagiarismCase() {
        return plagiarismCase;
    }

    public void setPlagiarismCase(PlagiarismCase plagiarismCase) {
        this.plagiarismCase = plagiarismCase;
    }

    public PlagiarismComparison getPlagiarismComparison() {
        return plagiarismComparison;
    }

    public void setPlagiarismComparison(PlagiarismComparison plagiarismComparison) {
        this.plagiarismComparison = plagiarismComparison;
    }

    @Override
    public String toString() {
        return "PlagiarismSubmission{" + "submissionId=" + submissionId + ", studentLogin='" + studentLogin + '\'' + ", size=" + size + ", score=" + score + '}';
    }
}
