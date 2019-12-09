package de.tum.in.www1.artemis.domain.participation;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A Participation.
 */
@Entity
@Table(name = "participation", uniqueConstraints = @UniqueConstraint(columnNames = { "student_id", "exercise_id", "initialization_state" }))
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "P")
// NOTE: Use strict cache to prevent lost updates when updating statistics in semaphore (see StatisticService.java)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
// Annotation necessary to distinguish between concrete implementations of Exercise when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = StudentParticipation.class, name = "student"),
        @JsonSubTypes.Type(value = ProgrammingExerciseStudentParticipation.class, name = "programming"),
        @JsonSubTypes.Type(value = TemplateProgrammingExerciseParticipation.class, name = "template"),
        @JsonSubTypes.Type(value = SolutionProgrammingExerciseParticipation.class, name = "solution"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Participation implements Serializable, ParticipationInterface {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "initialization_state")
    @JsonView(QuizView.Before.class)
    private InitializationState initializationState;

    @Column(name = "initialization_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime initializationDate;

    // NOTE: Keep default of FetchType.EAGER because most of the times we want
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
     * 2. Persist the submission: submissionRepository.save(submission) 3. Add the submission to the participation: participation.addSubmissions(submission) 4. Persist the
     * participation: participationRepository.save(participation) It is important that, if you want to persist the submission and the participation in the same transaction, you
     * have to use the save function and not the saveAndFlush function because otherwise an exception is thrown. We can think about adding orphanRemoval=true here, after adding the
     * participationId to all submissions.
     */
    @OneToMany(mappedBy = "participation")
    @JsonIgnoreProperties({ "participation", "result" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Submission> submissions = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public InitializationState getInitializationState() {
        return initializationState;
    }

    public Participation initializationState(InitializationState initializationState) {
        this.initializationState = initializationState;
        return this;
    }

    public void setInitializationState(InitializationState initializationState) {
        this.initializationState = initializationState;
    }

    public ZonedDateTime getInitializationDate() {
        return initializationDate;
    }

    public Participation initializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
        return this;
    }

    public void setInitializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
    }

    /**
     *
     * @return exercise object
     */
    public abstract Exercise getExercise();

    /**
     *
     * @param exercise that will be set
     */
    public abstract void setExercise(Exercise exercise);

    public Set<Result> getResults() {
        return results;
    }

    public Participation results(Set<Result> results) {
        this.results = results;
        return this;
    }

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

    public Set<Submission> getSubmissions() {
        return submissions;
    }

    public Participation submissions(Set<Submission> submissions) {
        this.submissions = submissions;
        return this;
    }

    public void addSubmissions(Submission submission) {
        this.submissions.add(submission);
        submission.setParticipation(this);
    }

    public Participation removeSubmissions(Submission submission) {
        this.submissions.remove(submission);
        submission.setParticipation(null);
        return this;
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
    public Result findLatestResult() {
        Set<Result> results = this.results;
        if (results == null || results.size() == 0) {
            return null;
        }
        List<Result> sortedResults = new ArrayList<>(results);
        sortedResults.sort((r1, r2) -> r2.getCompletionDate().compareTo(r1.getCompletionDate()));
        return sortedResults.get(0);
    }

    // TODO: implement a method Result findLatestResultBeforeDueDate(ZonedDateTime dueDate)

    /**
     * Finds the latest submission for the participation. Checks if the participation has any submissions. If there are no submissions, return null. Otherwise sort the submissions
     * by submission date and return the first. WARNING: The submissions of the participation might not be loaded because of Hibernate and therefore, the function might return
     * null, although the participation has submissions. This might not be high-performance, so use it at your own risk.
     *
     * @return the latest submission or null
     */
    public Optional<Submission> findLatestSubmission() {
        Set<Submission> submissions = this.submissions;
        if (submissions == null || submissions.size() == 0) {
            return Optional.empty();
        }

        return submissions.stream().max((s1, s2) -> {
            if (s1.getSubmissionDate() == null || s2.getSubmissionDate() == null) {
                // this case should not happen, but in the rare case we can compare the ids
                // newer ids are typically later
                return s1.getId().compareTo(s2.getId());
            }
            return s1.getSubmissionDate().compareTo(s2.getSubmissionDate());
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Participation participation = (Participation) o;
        if (participation.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), participation.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Participation{" + "id=" + id + ", initializationState=" + initializationState + ", initializationDate=" + initializationDate + ", results=" + results
                + ", submissions=" + submissions + '}';
    }

    /**
     * NOTE: do not use this in a transactional context and do not save the returned object to the database
     * This method is useful when we want to cut off attributes while sending entities to the client and we are only interested in the id of the object
     * We use polymorphism here, so subclasses should implement / override this method to create the correct object type
     * @return an empty participation just including the id of the object
     */
    public abstract Participation copyParticipationId();
}
