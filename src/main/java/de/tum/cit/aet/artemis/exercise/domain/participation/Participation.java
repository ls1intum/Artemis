package de.tum.cit.aet.artemis.exercise.domain.participation;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

/**
 * A Participation.
 */
@Entity
@Table(name = "participation", uniqueConstraints = { @UniqueConstraint(columnNames = { "student_id", "exercise_id", "initialization_state" }),
        @UniqueConstraint(columnNames = { "team_id", "exercise_id", "initialization_state" }) })
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "P")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of Exercise when deserializing from JSON
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = StudentParticipation.class, name = "student"),
    @JsonSubTypes.Type(value = ProgrammingExerciseStudentParticipation.class, name = "programming"),
    @JsonSubTypes.Type(value = TemplateProgrammingExerciseParticipation.class, name = "template"),
    @JsonSubTypes.Type(value = SolutionProgrammingExerciseParticipation.class, name = "solution"),
    @JsonSubTypes.Type(value = ExampleParticipation.class, name = "example"),
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Participation extends DomainObject implements ParticipationInterface {

    @Enumerated(EnumType.STRING)
    @Column(name = "initialization_state")
    private InitializationState initializationState;

    @Column(name = "initialization_date")
    private ZonedDateTime initializationDate;

    @Column(name = "individual_due_date")
    private ZonedDateTime individualDueDate;

    /**
     * Whether this participation belongs to an exam test run or practice mode.
     */
    @Column(name = "test_run")
    private Boolean testRun = false;

    // NOTE: Keep default of FetchType.EAGER because most of the time we want
    // to get a student participation, we also need the exercise. Dealing with Proxy
    // objects would cause more issues (Subclasses don't work properly for Proxy objects)
    // and the gain from fetching lazy here is minimal
    // Note: optional = false and nullable = false are required for orphanRemoval to work correctly
    // with NOT NULL FK constraints. When a participation is removed from an exercise's collection,
    // orphanRemoval causes a DELETE (not UPDATE to NULL) at flush time.
    @ManyToOne(optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    @JsonIgnoreProperties({ "studentParticipations", "tutorParticipations", "course" })
    protected Exercise exercise;

    /**
     * Because a submission has a reference to the participation and the participation has a collection of submissions, setting the cascade type to PERSIST would result in
     * exceptions, i.e., if you want to persist a submission, you have to follow these steps: 1. Set the participation of the submission: submission.setParticipation(participation)
     * 2. Persist the submission: submissionRepository.save(submission) 3. Add the submission to the participation: participation.addSubmission(submission) 4. Persist the
     * participation: participationRepository.save(participation) It is important that, if you want to persist the submission and the participation in the same transaction, you
     * have to use the save function and not the saveAndFlush function because otherwise an exception is thrown. We can think about adding orphanRemoval=true here, after adding the
     * participationId to all submissions.
     */
    @OneToMany(mappedBy = "participation", fetch = FetchType.LAZY)
    @JsonIgnoreProperties({ "participation" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Submission> submissions = new HashSet<>();

    /**
     * Graded course exercises, practice mode course exercises and real exam exercises always have only one parcitipation per exercise
     * In case of a test exam, there are multiple participations possible for one exercise
     * This field is necessary to preserve the constraint of one partipation per exercise, while allowing multiple particpiations per exercise for test exams
     * The value is 0 for graded course exercises and exercises in the real exams
     * The value is 1 for practice mode course exercises
     * The value is 0-255 for test exam exercises. For each subsequent participation the number is increased by one
     */
    @Column(name = "attempt")
    private int attempt = 0;

    /**
     * This property stores the total number of submissions in this participation. Not stored in the database, computed dynamically and used in showing statistics to the user in
     * the exercise view.
     */
    @Transient
    private Integer submissionCountTransient;

    public Integer getSubmissionCount() {
        return submissionCountTransient;
    }

    public void setSubmissionCount(Integer submissionCount) {
        this.submissionCountTransient = submissionCount;
    }

    @Override
    public InitializationState getInitializationState() {
        return initializationState;
    }

    public Participation initializationState(InitializationState initializationState) {
        this.initializationState = initializationState;
        return this;
    }

    @Override
    public void setInitializationState(InitializationState initializationState) {
        this.initializationState = initializationState;
    }

    @Override
    public ZonedDateTime getInitializationDate() {
        return initializationDate;
    }

    public Participation initializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
        return this;
    }

    @Override
    public void setInitializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
    }

    @Override
    public ZonedDateTime getIndividualDueDate() {
        return individualDueDate;
    }

    @Override
    public void setIndividualDueDate(ZonedDateTime individualDueDate) {
        this.individualDueDate = individualDueDate;
    }

    public boolean isTestRun() {
        return Boolean.TRUE.equals(testRun);
    }

    public void setTestRun(boolean testRun) {
        this.testRun = testRun;
    }

    /**
     * Same as {@link #isTestRun} since {@link Participation#testRun} is used to determine if a participation in a course exercise is used for practice purposes
     *
     * @return true if the participation is only used for practicing after the due date
     */
    @JsonIgnore
    public boolean isPracticeMode() {
        return Boolean.TRUE.equals(testRun);
    }

    /**
     * Same as {@link #setTestRun} since {@link Participation#testRun} is used to determine if a participation in a course exercise is used for practice purposes
     *
     * @param practiceMode sets the testRun flag to this value
     */
    public void setPracticeMode(boolean practiceMode) {
        this.testRun = practiceMode;
    }

    @Override
    public Set<Submission> getSubmissions() {
        return submissions;
    }

    public Participation submissions(Set<Submission> submissions) {
        this.submissions = submissions;
        return this;
    }

    @Override
    public void addSubmission(Submission submission) {
        this.submissions.add(submission);
        submission.setParticipation(this);
    }

    public void setSubmissions(Set<Submission> submissions) {
        this.submissions = submissions;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    /**
     * Finds the latest result for the participation. Checks if the participation has any results. If there are no results, return null. Otherwise sort the results by completion
     * date and return the first. WARNING: The results of the participation might not be loaded because of Hibernate and therefore, the function might return null, although the
     * participation has results. This might not be high-performance, so use it at your own risk.
     *
     * @return the latest result or null
     */
    public Result findLatestResult() {
        var latestSubmission = this.findLatestSubmission();
        return latestSubmission.map(Submission::getLatestResult).orElse(null);
    }

    /**
     * Finds the latest submission for the participation. Checks if the participation has any submissions. If there are no submissions, return null.
     * Otherwise sort the submissions by submission date and return the first. WARNING: The submissions of the participation might not be loaded because of Hibernate and
     * therefore, the function might return null, although the participation has submissions. This might not be high-performance, so use it at your own risk.
     *
     * @param <T> submission type
     * @return the latest submission or null
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Submission> Optional<T> findLatestSubmission() {
        Set<Submission> submissions = this.submissions;
        if (submissions == null || submissions.isEmpty()) {
            return Optional.empty();
        }
        return (Optional<T>) submissions.stream().max(Comparator.naturalOrder());
    }

    /**
     * Adds the prefix "-practice" to the given name, if this is a test run that might be used for practice
     *
     * @param string the string that might get "practice-" added its front
     * @return the same string with "practice-" added to the front if this is a test run participation
     */
    public String addPracticePrefixIfTestRun(String string) {
        return (isPracticeMode() ? "practice-" : "") + string;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "id=" + getId() + ", initializationState=" + initializationState + ", initializationDate=" + initializationDate + ", submissions="
                + submissions + ", submissionCount=" + submissionCountTransient + "}";
    }

    public abstract void filterSensitiveInformation();

    @JsonIgnore
    public abstract String getType();

}
