package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.*;
import com.google.common.collect.Sets;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * A Exercise.
 */
@Entity
@Table(name = "exercise")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name = "discriminator",
    discriminatorType = DiscriminatorType.STRING
)
@DiscriminatorValue(value = "E")
// NOTE: Use strict cache to prevent lost updates when updating statistics in semaphore (see StatisticService.java)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
// Annotation necessary to distinguish between concrete implementations of Exercise when deserializing from JSON
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProgrammingExercise.class, name = "programming"),
    @JsonSubTypes.Type(value = ModelingExercise.class, name = "modeling"),
    @JsonSubTypes.Type(value = QuizExercise.class, name = "quiz"),
    @JsonSubTypes.Type(value = TextExercise.class, name = "text"),
    @JsonSubTypes.Type(value = FileUploadExercise.class, name = "file-upload"),
})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Exercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "short_name")
    @JsonView(QuizView.Before.class)
    private String shortName;

    @Column(name = "release_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime releaseDate;

    @Column(name = "due_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime dueDate;

    @Column(name = "assessment_due_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime assessmentDueDate;

    @Column(name = "max_score")
    private Double maxScore;

    @Column(name = "problem_statement")
    @Lob
    private String problemStatement;

    @Column(name = "grading_instructions")
    @Lob
    private String gradingInstructions;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> categories = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    private DifficultyLevel difficulty;

    @OneToMany(mappedBy = "exercise")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<Participation> participations = new HashSet<>();
    
    @OneToMany(mappedBy = "assessedExercise")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties("assessedExercise")
    private Set<TutorParticipation> tutorParticipations = new HashSet<>();

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Course course;

    @OneToMany(mappedBy = "exercise")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<ExampleSubmission> exampleSubmissions = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public Exercise title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortName() {
        return shortName;
    }

    public Exercise shortName(String shortName) {
        this.shortName = shortName;
        return this;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public Exercise releaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
        return this;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public Exercise dueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
        return this;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public ZonedDateTime getAssessmentDueDate() {
        return assessmentDueDate;
    }

    public Exercise assessmentDueDate(ZonedDateTime assessmentDueDate) {
        this.assessmentDueDate = assessmentDueDate;
        return this;
    }

    public void setAssessmentDueDate(ZonedDateTime assessmentDueDate) {
        this.assessmentDueDate = assessmentDueDate;
    }

    public Double getMaxScore() {
        return maxScore;
    }

    public Exercise maxScore(Double maxScore) {
        this.maxScore = maxScore;
        return this;
    }

    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public Exercise problemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
        return this;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    public String getGradingInstructions() {
        return gradingInstructions;
    }

    public Exercise gradingInstructions(String gradingInstructions) {
        this.gradingInstructions = gradingInstructions;
        return this;
    }

    public void setGradingInstructions(String gradingInstructions) {
        this.gradingInstructions = gradingInstructions;
    }


    public DifficultyLevel getDifficulty() {
        return difficulty;
    }

    public Exercise difficulty(DifficultyLevel difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public void setDifficulty(DifficultyLevel difficulty) {
        this.difficulty = difficulty;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public Set<Participation> getParticipations() {
        return participations;
    }

    public Exercise participations(Set<Participation> participations) {
        this.participations = participations;
        return this;
    }

    public Exercise addParticipation(Participation participation) {
        this.participations.add(participation);
        participation.setExercise(this);
        return this;
    }

    public Exercise removeParticipation(Participation participation) {
        this.participations.remove(participation);
        participation.setExercise(null);
        return this;
    }

    public void setParticipations(Set<Participation> participations) {
        this.participations = participations;
    }

    public Course getCourse() {
        return course;
    }

    public Exercise course(Course course) {
        this.course = course;
        return this;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<ExampleSubmission> getExampleSubmissions() {
        return exampleSubmissions;
    }

    public Exercise exampleSubmissions(Set<ExampleSubmission> exampleSubmissions) {
        this.exampleSubmissions = exampleSubmissions;
        return this;
    }

    public Exercise addExampleSubmission(ExampleSubmission exampleSubmission) {
        this.exampleSubmissions.add(exampleSubmission);
        exampleSubmission.setExercise(this);
        return this;
    }

    public Exercise removeExampleSubmission(ExampleSubmission exampleSubmission) {
        this.exampleSubmissions.remove(exampleSubmission);
        exampleSubmission.setExercise(null);
        return this;
    }

    public void setExampleSubmissions(Set<ExampleSubmission> exampleSubmissions) {
        this.exampleSubmissions = exampleSubmissions;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public Boolean isEnded() {
        if (getDueDate() == null) {
            return false;
        }
        return ZonedDateTime.now().isAfter(getDueDate());
    }

    /**
     * check if students are allowed to see this exercise
     *
     * @return true, if students are allowed to see this exercise, otherwise false
     */
    @JsonView(QuizView.Before.class)
    public Boolean isVisibleToStudents() {
        if (releaseDate == null) {  //no release date means the exercise is visible to students
            return true;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

    /**
     * can be invoked to make sure that sensitive information is not sent to the client
     */
    public void filterSensitiveInformation() {
        setGradingInstructions(null);
    }

    /**
     * find a relevant participation for this exercise
     * (relevancy depends on InitializationState)
     *
     * @param participations the list of available participations
     * @return the found participation, or null, if none exist
     */
    public Participation findRelevantParticipation(List<Participation> participations) {
        Participation relevantParticipation = null;
        for (Participation participation : participations) {
            if (participation.getExercise() != null && participation.getExercise().equals(this)) {
                if (participation.getInitializationState() == InitializationState.INITIALIZED) {
                    // InitializationState INITIALIZED is preferred
                    // => if we find one, we can return immediately
                    return participation;
                } else if (participation.getInitializationState() == InitializationState.INACTIVE) {
                    // InitializationState INACTIVE is also ok
                    // => if we can't find INITIALIZED, we return that one
                    relevantParticipation = participation;
                } else if (participation.getExercise() instanceof ModelingExercise || participation.getExercise() instanceof TextExercise) {
                    return participation;
                }
            }
        }
        return relevantParticipation;
    }

    /**
     * Get the latest relevant result from the given participation (rated == true or rated == null)
     * (relevancy depends on Exercise type => this should be overridden by subclasses if necessary)
     *
     * @param participation the participation whose results we are considering
     * @return the latest relevant result in the given participation, or null, if none exist
     */
    public Result findLatestRatedResultWithCompletionDate(Participation participation) {
        // for most types of exercises => return latest result (all results are relevant)
        Result latestResult = null;
        for (Result result : participation.getResults()) {
            //NOTE: for the dashboard we only use rated results with completion date
            //TODO: result.isRated() == null is a compatibility mechanism that we should deactivate soon
            if (result.getCompletionDate() != null && (result.isRated() == null || result.isRated() == Boolean.TRUE)) {
                //take the first found result that fulfills the above requirements
                if (latestResult == null) {
                    latestResult = result;
                }
                //take newer results and thus disregard older ones
                else if (latestResult.getCompletionDate().isBefore(result.getCompletionDate())) {
                    latestResult = result;
                }
            }
        }
        return latestResult;
    }

    /**
     * Returns all results of an exercise for give participation.
     * If the exercise is restricted like {@link QuizExercise} please override this function with the respective filter.
     * (relevancy depends on Exercise type => this should be overridden by subclasses if necessary)
     *
     * @param participation the participation whose results we are considering
     * @return all results of given participation, or null, if none exist
     */
    public Set<Result> findResultsFilteredForStudents(Participation participation) {
        return participation.getResults();
    }

    /**
     * Find the participation in participations that belongs to the given exercise
     * that includes the exercise data, plus the found participation with its most recent relevant result.
     * Filter everything else that is not relevant
     *
     * @param participations the set of participations, wherein to search for the relevant participation
     * @param username
     */
    public void filterForCourseDashboard(List<Participation> participations, String username) {

        // remove the unnecessary inner course attribute
        setCourse(null);

        // get user's participation for the exercise
        Participation participation = findRelevantParticipation(participations);

        // for quiz exercises also check SubmissionHashMap for submission by this user (active participation)
        // if participation was not found in database
        if (participation == null && this instanceof QuizExercise) {
            QuizSubmission submission = QuizScheduleService.getQuizSubmission(getId(), username);
            if (submission.getSubmissionDate() != null) {
                participation = new Participation().exercise(this).initializationState(InitializationState.INITIALIZED);
            }
        }

        // add results to participation
        if (participation != null) {

            // only transmit the relevant result
            Result result = participation.getExercise().findLatestRatedResultWithCompletionDate(participation);
            Set<Result> results = result != null ? Sets.newHashSet(result) : Sets.newHashSet();

            // add results to json
            if (result != null) {
                // remove inner participation from result
                result.setParticipation(null);
            }
            participation.setResults(results);
            // remove inner exercise from participation
            participation.setExercise(null);

            // add participation into an array
            setParticipations(Sets.newHashSet(participation));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Exercise exercise = (Exercise) o;
        if (exercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), exercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }


    @Override
    public String toString() {
        return "Exercise{" +
            "id=" + getId() +
            ", problemStatement='" + getProblemStatement() + "'" +
            ", gradingInstructions='" + getGradingInstructions() + "'" +
            ", title='" + getTitle() + "'" +
            ", shortName='" + getShortName() + "'" +
            ", releaseDate='" + getReleaseDate() + "'" +
            ", dueDate='" + getDueDate() + "'" +
            ", assessmentDueDate='" + getAssessmentDueDate() + "'" +
            ", maxScore=" + getMaxScore() +
            ", difficulty='" + getDifficulty() + "'" +
            ", categories='" + getCategories() + "'" +
            "}";
    }

    public Set<TutorParticipation> getTutorParticipations() {
        return tutorParticipations;
    }

    public void setTutorParticipations(Set<TutorParticipation> tutorParticipations) {
        this.tutorParticipations = tutorParticipations;
    }
}
