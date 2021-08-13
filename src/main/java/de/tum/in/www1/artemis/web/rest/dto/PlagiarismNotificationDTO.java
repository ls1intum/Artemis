package de.tum.in.www1.artemis.web.rest.dto;

import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude
public class PlagiarismNotificationDTO {

    private String studentLogin;

    private Long plagiarismComparisonId;

    private String instructorMessage;

    public PlagiarismNotificationDTO(String studentLogin, Long plagiarismComparisonId, String instructorMessage) {
        this.studentLogin = studentLogin;
        this.plagiarismComparisonId = plagiarismComparisonId;
        this.instructorMessage = instructorMessage;
    }

    public String getInstructorMessage() {
        return instructorMessage;
    }

    public void setInstructorMessage(String instructorMessage) {
        this.instructorMessage = instructorMessage;
    }

    public String getStudentLogin() {
        return studentLogin;
    }

    public void setStudentLogin(String studentLogin) {
        this.studentLogin = studentLogin;
    }

    public Long getPlagiarismComparisonId() {
        return plagiarismComparisonId;
    }

    public void setPlagiarismComparisonId(Long plagiarismComparisonId) {
        this.plagiarismComparisonId = plagiarismComparisonId;
    }
}
