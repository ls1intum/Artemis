package de.tum.in.www1.artemis.domain.exam;

import java.time.ZonedDateTime;
import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "student_exam")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentExam extends AbstractAuditingEntity {

    @Column(name = "submitted")
    private Boolean submitted;

    /**
     * The working time in seconds
     */
    @Column(name = "working_time")
    private Integer workingTime;

    @Column(name = "started")
    private Boolean started;

    @Column(name = "started_date")
    private ZonedDateTime startedDate;

    @Column(name = "submission_date")
    private ZonedDateTime submissionDate;

    @Column(name = "test_run")
    private Boolean testRun;

    @ManyToOne
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToMany
    @JoinTable(name = "student_exam_exercise", joinColumns = @JoinColumn(name = "student_exam_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "exercise_id", referencedColumnName = "id"))
    @OrderColumn(name = "exercise_order")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<Exercise> exercises = new ArrayList<>();

    @OneToMany(mappedBy = "studentExam", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("studentExam")
    private Set<ExamSession> examSessions = new HashSet<>();

    public Boolean isSubmitted() {
        return submitted;
    }

    public boolean isTestRun() {
        return Boolean.TRUE.equals(testRun);
    }

    public void setTestRun(boolean testRun) {
        this.testRun = testRun;
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

    public Boolean isStarted() {
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

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(List<Exercise> exercises) {
        this.exercises = exercises;
    }

    public StudentExam addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        return this;
    }

    public StudentExam removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        return this;
    }

    public Set<ExamSession> getExamSessions() {
        return examSessions;
    }

    public void setExamSessions(Set<ExamSession> examSessions) {
        this.examSessions = examSessions;
    }

    /**
     * check if the individual student exam has ended (based on the working time)
     *
     * @return true if the exam has finished, otherwise false, null if this cannot be determined
     */
    public Boolean isEnded() {
        if (this.getExam() == null || this.getExam().getStartDate() == null || this.getWorkingTime() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(testRun)) {
            return false;
        }
        return ZonedDateTime.now().isAfter(getIndividualEndDate());
    }

    /**
     * Returns the individual exam end date taking the working time of this student exam into account
     *
     * @return the ZonedDateTime that marks the exam end for this student (excluding grace period)
     */
    @JsonIgnore
    public ZonedDateTime getIndividualEndDate() {
        return exam.getStartDate().plusSeconds(workingTime);
    }

    /**
     * Returns the individual exam end date taking the working time of this student exam into account and the grace period set for this exam
     *
     * @return the ZonedDateTime that marks the exam end for this student, including the exam's grace period
     */
    @JsonIgnore
    public ZonedDateTime getIndividualEndDateWithGracePeriod() {
        int gracePeriodInSeconds = Objects.requireNonNullElse(exam.getGracePeriod(), 0);
        return exam.getStartDate().plusSeconds(workingTime + gracePeriodInSeconds);
    }

    /**
     * Calls {@link Exam#resultsPublished()}
     * @return true the results are published
     */
    @JsonIgnore
    public boolean areResultsPublishedYet() {
        return exam.resultsPublished();
    }
}
