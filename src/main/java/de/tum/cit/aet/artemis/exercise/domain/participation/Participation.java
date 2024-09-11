package de.tum.cit.aet.artemis.exercise.domain.participation;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
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
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.enumeration.InitializationState;
import de.tum.cit.aet.artemis.domain.enumeration.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.quiz.config.QuizView;

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
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Participation extends DomainObject implements ParticipationInterface {

    @Enumerated(EnumType.STRING)
    @Column(name = "initialization_state")
    @JsonView(QuizView.Before.class)
    private InitializationState initializationState;

    @Column(name = "initialization_date")
    @JsonView(QuizView.Before.class)
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
    @ManyToOne
    @JsonIgnoreProperties("studentParticipations")
    @JsonView(QuizView.Before.class)
    protected Exercise exercise;

    /**
     * Results are not cascaded through the participation because ideally we want the relationship between participations, submissions and results as follows: each participation
     * has multiple submissions. For each submission there can be a result. Therefore, the result is persisted with the submission. Refer to Submission.result for cascading
     * settings.
     */
    @OneToMany(mappedBy = "participation")
    @JsonIgnoreProperties(value = "participation", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private Set<Result> results = new HashSet<>();

    /**
     * Because a submission has a reference to the participation and the participation has a collection of submissions, setting the cascade type to PERSIST would result in
     * exceptions, i.e., if you want to persist a submission, you have to follow these steps: 1. Set the participation of the submission: submission.setParticipation(participation)
     * 2. Persist the submission: submissionRepository.save(submission) 3. Add the submission to the participation: participation.addSubmission(submission) 4. Persist the
     * participation: participationRepository.save(participation) It is important that, if you want to persist the submission and the participation in the same transaction, you
     * have to use the save function and not the saveAndFlush function because otherwise an exception is thrown. We can think about adding orphanRemoval=true here, after adding the
     * participationId to all submissions.
     */
    @OneToMany(mappedBy = "participation")
    @JsonIgnoreProperties({ "participation" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Submission> submissions = new HashSet<>();

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

    public Set<Result> getResults() {
        return results;
    }

    public Participation results(Set<Result> results) {
        this.results = results;
        return this;
    }

    @Override
    public void addResult(Result result) {
        this.results.add(result);
        result.setParticipation(this);
    }

    public void removeResult(Result result) {
        this.results.remove(result);
        result.setParticipation(null);
    }

    public void setResults(Set<Result> results) {
        this.results = results;
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

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    /**
     * Finds the latest result for the participation. Checks if the participation has any results. If there are no results, return null. Otherwise sort the results by completion
     * date and return the first. WARNING: The results of the participation might not be loaded because of Hibernate and therefore, the function might return null, although the
     * participation has results. This might not be high-performance, so use it at your own risk.
     *
     * @return the latest result or null
     */
    @Nullable
    public Result findLatestLegalResult() {
        return findLatestResult(true);
    }

    @Nullable
    public Result findLatestResult() {
        return findLatestResult(false);
    }

    /**
     * Like findLatestLegalResult() but with the possibility to include illegal submissions,
     *
     * @param filterIllegalResults should illegal submissions be excluded in the search
     * @return the latest result or null
     */
    @Nullable
    private Result findLatestResult(boolean filterIllegalResults) {
        Set<Result> results = this.results;
        if (results == null || results.isEmpty()) {
            return null;
        }

        if (filterIllegalResults) {
            // Filter out results that belong to an illegal submission (if the submission exists).
            results = results.stream().filter(result -> result.getSubmission() == null || !SubmissionType.ILLEGAL.equals(result.getSubmission().getType()))
                    .collect(Collectors.toSet());
        }

        List<Result> sortedResultsWithCompletionDate = results.stream().filter(r -> r.getCompletionDate() != null)
                .sorted(Comparator.comparing(Result::getCompletionDate).reversed()).toList();

        if (sortedResultsWithCompletionDate.isEmpty()) {
            return null;
        }
        return sortedResultsWithCompletionDate.getFirst();
    }

    /**
     * Finds the latest legal submission for the participation. Legal means that ILLEGAL submissions (exam exercise submissions after the end date)
     * are not used. Checks if the participation has any submissions. If there are no submissions, return null. Otherwise sort the submissions
     * by submission date and return the first. WARNING: The submissions of the participation might not be loaded because of Hibernate and therefore, the function might return
     * null, although the participation has submissions. This might not be high-performance, so use it at your own risk.
     *
     * @param <T> submission type
     * @return the latest submission or null
     */
    @Override
    public <T extends Submission> Optional<T> findLatestSubmission() {
        return findLatestSubmission(false);
    }

    /**
     * Finds the latest legal or illegal submission or null if non exist.
     *
     * @param <T> submission type
     * @return the latest submission or null
     */
    public <T extends Submission> Optional<T> findLatestLegalOrIllegalSubmission() {
        return findLatestSubmission(true);
    }

    /**
     * Like {@link Participation#findLatestSubmission()} but with the possibility to include illegal submissions,
     *
     * @param <T>                       submission type
     * @param includeIllegalSubmissions should the function include illegal submission
     * @return the latest submission or null
     */
    private <T extends Submission> Optional<T> findLatestSubmission(boolean includeIllegalSubmissions) {
        Set<Submission> submissions = this.submissions;
        if (submissions == null || submissions.isEmpty()) {
            return Optional.empty();
        }

        if (!includeIllegalSubmissions) {
            submissions = submissions.stream().filter(submission -> !SubmissionType.ILLEGAL.equals(submission.getType())).collect(Collectors.toSet());
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
        return getClass().getSimpleName() + "{" + "id=" + getId() + ", initializationState=" + initializationState + ", initializationDate=" + initializationDate + ", results="
                + results + ", submissions=" + submissions + ", submissionCount=" + submissionCountTransient + "}";
    }

    public abstract void filterSensitiveInformation();

    @JsonIgnore
    public abstract String getType();
}
