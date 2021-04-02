package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.*;
import java.util.stream.Collectors;

import javax.persistence.*;

import jplag.Submission;

import org.slf4j.*;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingSubmissionElement;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;

@Entity
@Table(name = "plagiarism_submission")
public class PlagiarismSubmission<E extends PlagiarismSubmissionElement> extends DomainObject {

    private static final Logger logger = LoggerFactory.getLogger(PlagiarismSubmission.class);

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
    private long submissionId;

    /**
     * Login of the student who created the submission.
     */
    private String studentLogin;

    /**
     * List of elements the related submission consists of.
     */
    @OneToMany(cascade = CascadeType.ALL, targetEntity = PlagiarismSubmissionElement.class, fetch = FetchType.EAGER)
    @JoinTable(name = "plagiarism_submission_elements", joinColumns = @JoinColumn(name = "plagiarism_submission_id"), inverseJoinColumns = @JoinColumn(name = "plagiarism_submission_element_id"))
    private List<E> elements;

    /**
     * Size of the related submission.
     * <p>
     * For modeling submissions, this is the number of modeling elements. For text and programming
     * submissions, this is the number of words or tokens.
     */
    private int size;

    /**
     * Result score of the related submission.
     */
    private Double score;

    /**
     * Create a new PlagiarismSubmission instance from an existing JPlag Submission
     *
     * @param jplagSubmission the JPlag Submission to create the PlagiarismSubmission from
     * @return a new PlagiarismSubmission instance
     */
    public static PlagiarismSubmission<TextSubmissionElement> fromJPlagSubmission(Submission jplagSubmission) {
        PlagiarismSubmission<TextSubmissionElement> submission = new PlagiarismSubmission<>();

        String[] submissionIdAndStudentLogin = jplagSubmission.name.split("[-.]");

        long submissionId = 0;
        String studentLogin = "unknown";

        if (submissionIdAndStudentLogin.length >= 2) {
            try {
                submissionId = Long.parseLong(submissionIdAndStudentLogin[0]);
            }
            catch (NumberFormatException e) {
                logger.error("Invalid submissionId: " + e.getMessage());
            }

            studentLogin = submissionIdAndStudentLogin[1];
        }

        submission.setStudentLogin(studentLogin);
        submission.setElements(Arrays.stream(jplagSubmission.tokenList.tokens).filter(Objects::nonNull).map(TextSubmissionElement::fromJPlagToken).collect(Collectors.toList()));
        submission.setSubmissionId(submissionId);
        submission.setSize(jplagSubmission.tokenList.tokens.length);
        submission.setScore(null); // TODO

        return submission;
    }

    /**
     * Create a new PlagiarismSubmission instance from an existing Modeling Submission
     *
     * @param modelingSubmission the Modeling Submission to create the PlagiarismSubmission from
     * @return a new PlagiarismSubmission instance
     */
    public static PlagiarismSubmission<ModelingSubmissionElement> fromModelingSubmission(ModelingSubmission modelingSubmission) {
        PlagiarismSubmission<ModelingSubmissionElement> submission = new PlagiarismSubmission<>();

        submission.setSubmissionId(modelingSubmission.getId());
        submission.setStudentLogin(((StudentParticipation) modelingSubmission.getParticipation()).getParticipantIdentifier());

        if (modelingSubmission.getLatestResult() != null) {
            submission.setScore(modelingSubmission.getLatestResult().getScore());
        }

        return submission;
    }

    public String getStudentLogin() {
        return studentLogin;
    }

    public void setStudentLogin(String studentLogin) {
        this.studentLogin = studentLogin;
    }

    public List<E> getElements() {
        return elements;
    }

    public void setElements(List<E> elements) {
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
}
