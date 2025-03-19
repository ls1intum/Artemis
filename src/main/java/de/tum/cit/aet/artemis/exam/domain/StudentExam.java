package de.tum.cit.aet.artemis.exam.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

@Entity
@Table(name = "student_exam")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentExam extends AbstractAuditingEntity {

    @Column(name = "submitted")
    private Boolean submitted;

    /**
     * The individual working time per student in seconds
     * The default working time of an exam is stored in exam.workingTime
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

    @ManyToMany
    @JoinTable(name = "student_exam_quiz_question", joinColumns = @JoinColumn(name = "student_exam_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "quiz_question_id", referencedColumnName = "id"))
    private List<QuizQuestion> quizQuestions = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "student_exam_participation", joinColumns = @JoinColumn(name = "student_exam_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "participation_id", referencedColumnName = "id"))
    @OrderColumn(name = "participation_order")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<StudentParticipation> studentParticipations = new ArrayList<>();

    public Boolean isSubmitted() {
        return submitted;
    }

    public boolean isTestRun() {
        return Boolean.TRUE.equals(testRun);
    }

    @JsonIgnore
    public boolean isTestExam() {
        return exam.isTestExam();
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

    /**
     * Sets the started flag to true and sets the startedDate to the current date
     *
     * @param startedDate the date when the exam was started
     */
    public void setStartedAndStartDate(ZonedDateTime startedDate) {
        this.started = true;
        this.startedDate = startedDate;
    }

    public ZonedDateTime getStartedDate() {
        return startedDate;
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
     * Returns a list of quiz questions associated with the student exam.
     * If the quizQuestions list is not null and has been initialized, it returns the list of quiz questions.
     * Otherwise, it returns an empty list.
     *
     * @return the list of quiz questions associated with the student exam
     */
    public List<QuizQuestion> getQuizQuestions() {
        if (quizQuestions != null && Hibernate.isInitialized(quizQuestions)) {
            return quizQuestions;
        }
        return Collections.emptyList();
    }

    public void setQuizQuestions(List<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
    }

    public List<StudentParticipation> getStudentParticipations() {
        return studentParticipations;
    }

    public void setStudentParticipations(List<StudentParticipation> studentParticipations) {
        this.studentParticipations = studentParticipations;
    }

    /**
     * Adds the given exam session to the student exam
     *
     * @param examSession the exam session to add
     * @return the student exam with the added exam session
     */
    public StudentExam addExamSession(ExamSession examSession) {
        this.examSessions.add(examSession);
        examSession.setStudentExam(this);
        return this;
    }

    /**
     * check if the individual student exam has ended (based on the working time)
     * For test exams, we cannot use exam.startTime, but need to use the student.startedDate. If this is not yet set,
     * the studentExams has not yet started and therefore cannot be ended.
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
        if (this.getExam().isTestExam() && !Boolean.TRUE.equals(this.started) && this.startedDate == null) {
            return false;
        }
        return ZonedDateTime.now().isAfter(getIndividualEndDate());
    }

    /**
     * Check if the individual student exam is finished
     * A student exam is finished if it's started and either submitted or the time has passed
     *
     * @return true if the exam is finished, otherwise false
     */
    public boolean isFinished() {
        return Boolean.TRUE.equals(this.isStarted()) && (Boolean.TRUE.equals(this.isEnded()) || Boolean.TRUE.equals(this.isSubmitted()));
    }

    /**
     * Returns the individual exam end date taking the working time of this student exam into account.
     * For test exams, the startedDate needs to be defined as this is not equal to exam.startDate
     *
     * @return the ZonedDateTime that marks the exam end for this student (excluding grace period), or null for test exams with undefined startedDate
     */
    @JsonIgnore
    public ZonedDateTime getIndividualEndDate() {
        if (exam.isTestExam()) {
            if (this.startedDate == null) {
                return null;
            }
            return this.startedDate.plusSeconds(workingTime);
        }
        return exam.getStartDate().plusSeconds(workingTime);
    }

    /**
     * Returns the individual exam end date taking the working time of this student exam into account and the grace period set for this exam
     *
     * @return the ZonedDateTime that marks the exam end for this student, including the exam's grace period, or null for test exams with undefined startedDate
     */
    @JsonIgnore
    public ZonedDateTime getIndividualEndDateWithGracePeriod() {
        int gracePeriodInSeconds = Objects.requireNonNullElse(exam.getGracePeriod(), 0);
        if (exam.isTestExam()) {
            if (this.startedDate == null) {
                return null;
            }
            return this.startedDate.plusSeconds(workingTime + gracePeriodInSeconds);
        }
        return exam.getStartDate().plusSeconds(workingTime + gracePeriodInSeconds);
    }

    /**
     * Determines if the Results of an Exam are already published
     * For a test exam, the Results are automatically published once the studentExam was submitted
     * For real exams, {@link Exam#resultsPublished()} is called
     *
     * @return true the results are published
     */
    @JsonIgnore
    public boolean areResultsPublishedYet() {
        if (this.exam.isTestExam()) {
            return (this.submitted != null && this.submitted);
        }
        else {
            return exam.resultsPublished();
        }
    }

}
