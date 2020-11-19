package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jplag.Submission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;

public class PlagiarismSubmission<E extends PlagiarismSubmissionElement> {

    /**
     * Login of the student who created the submission.
     */
    private String studentLogin;

    /**
     * List of elements the related submission consists of.
     */
    private List<E> elements;

    /**
     * ID of the related submission.
     */
    private long submissionId;

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
    private int score;

    public static PlagiarismSubmission<TextSubmissionElement> fromJPlagSubmission(Submission jplagSubmission) {
        PlagiarismSubmission<TextSubmissionElement> submission = new PlagiarismSubmission<>();

        // TODO: Check length of returned String[]
        String[] submissionIdAndStudentLogin = jplagSubmission.name.split("-");
        long submissionId = Long.parseLong(submissionIdAndStudentLogin[0]);
        String studentLogin = submissionIdAndStudentLogin[1];

        submission.setStudentLogin(studentLogin);
        submission.setElements(Arrays.stream(jplagSubmission.tokenList.tokens).map(TextSubmissionElement::fromJPlagToken).collect(Collectors.toList()));
        submission.setSubmissionId(submissionId);
        submission.setSize(jplagSubmission.tokenList.tokens.length);
        submission.setScore(0); // TODO

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

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }
}
