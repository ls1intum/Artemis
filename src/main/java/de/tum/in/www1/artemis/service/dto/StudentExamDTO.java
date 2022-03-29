package de.tum.in.www1.artemis.service.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

/**
 * DTO for a student exam and all its properties
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentExamDTO extends AuditingEntityDTO {

    private Long id;

    private Boolean submitted;

    private Integer workingTime;

    private Boolean started;

    private ZonedDateTime startedDate;

    private ZonedDateTime submissionDate;

    private Boolean testRun;

    private Exam exam;

    private UserDTO user;

    private List<Exercise> exercises;

    private Set<ExamSession> examSessions;

    /**
     * Jackson constructor
     */
    public StudentExamDTO() {
    }

    public StudentExamDTO(StudentExam studentExam) {
        this(studentExam.getId(), studentExam.isSubmitted(), studentExam.getWorkingTime(), studentExam.isStarted(), studentExam.getStartedDate(), studentExam.getSubmissionDate(),
                studentExam.isTestRun(), studentExam.getExam(), studentExam.getUser(), studentExam.getExercises(), studentExam.getExamSessions());
    }

    public StudentExamDTO(Long id, Boolean submitted, Integer workingTime, Boolean started, ZonedDateTime startedDate, ZonedDateTime submissionDate, Boolean testRun, Exam exam,
            User user, List<Exercise> exercises, Set<ExamSession> examSessions) {
        this.id = id;
        this.submitted = submitted;
        this.workingTime = workingTime;
        this.started = started;
        this.startedDate = startedDate;
        this.submissionDate = submissionDate;
        this.testRun = testRun;
        this.exam = exam;
        this.user = new UserDTO(user);
        this.exercises = exercises;
        this.examSessions = examSessions;
    }

    public Boolean getSubmitted() {
        return submitted;
    }

    public void setSubmitted(Boolean submitted) {
        this.submitted = submitted;
    }

    public Integer getWorkingTime() {
        return workingTime;
    }

    public void setWorkingTime(Integer workingTime) {
        this.workingTime = workingTime;
    }

    public Boolean getStarted() {
        return started;
    }

    public void setStarted(Boolean started) {
        this.started = started;
    }

    public ZonedDateTime getStartedDate() {
        return startedDate;
    }

    public void setStartedDate(ZonedDateTime startedDate) {
        this.startedDate = startedDate;
    }

    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public Boolean getTestRun() {
        return testRun;
    }

    public void setTestRun(Boolean testRun) {
        this.testRun = testRun;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    public List<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(List<Exercise> exercises) {
        this.exercises = exercises;
    }

    public Set<ExamSession> getExamSessions() {
        return examSessions;
    }

    public void setExamSessions(Set<ExamSession> examSessions) {
        this.examSessions = examSessions;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
