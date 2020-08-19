package de.tum.in.www1.artemis.web.rest.dto;

public class ModelingSubmissionComparisonElement {

    private Long submissionId;

    private Long score;

    private int size;

    private String studentLogin;

    public Long getSubmissionId() {
        return submissionId;
    }

    public Long getScore() {
        return score;
    }

    public int getSize() {
        return size;
    }

    public String getStudentLogin() {
        return studentLogin;
    }

    public ModelingSubmissionComparisonElement submissionId(Long submissionId) {
        this.submissionId = submissionId;
        return this;
    }

    public ModelingSubmissionComparisonElement score(Long score) {
        this.score = score;
        return this;
    }

    public ModelingSubmissionComparisonElement size(int size) {
        this.size = size;
        return this;
    }

    public ModelingSubmissionComparisonElement studentLogin(String studentLogin) {
        this.studentLogin = studentLogin;
        return this;
    }
}
