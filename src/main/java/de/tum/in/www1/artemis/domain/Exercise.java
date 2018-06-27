package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    @JsonSubTypes.Type(value = ProgrammingExercise.class, name = "programming-exercise"),
    @JsonSubTypes.Type(value = ModelingExercise.class, name = "modeling-exercise"),
    @JsonSubTypes.Type(value = QuizExercise.class, name = "quiz-exercise")
})
public abstract class Exercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "release_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime releaseDate;

    @Column(name = "due_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime dueDate;

    @Column(name = "max_score")
    private Double maxScore;

    @OneToMany(mappedBy = "exercise")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<Participation> participations = new HashSet<>();

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Course course;

    @Transient
    private boolean isOpenForSubmission;

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

    public boolean isOpenForSubmission() {
        if (dueDate != null) {
            return ZonedDateTime.now().isBefore(dueDate);
        }
        return true;
    }

    public void setOpenForSubmission(boolean openForSubmission) {
        isOpenForSubmission = openForSubmission;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
     * find a relevant participation for this exercise
     * (relevancy depends on ParticipationState)
     *
     * @param participations the list of available participations
     * @return the found participation, or null, if none exist
     */
    public Participation findRelevantParticipation(List<Participation> participations) {
        Participation relevantParticipation = null;
        for (Participation participation : participations) {
            if (participation.getExercise().equals(this)) {
                if (participation.getInitializationState() == ParticipationState.INITIALIZED) {
                    // ParticipationState INITIALIZED is preferred
                    // => if we find one, we can return immediately
                    return participation;
                } else if (participation.getInitializationState() == ParticipationState.INACTIVE) {
                    // ParticipationState INACTIVE is also ok
                    // => if we can't find INITIALIZED, we return that one
                    relevantParticipation = participation;
                } else if (participation.getExercise() instanceof ModelingExercise) {
                    return participation;
                }
            }
        }
        return relevantParticipation;
    }

    /**
     * Get the latest relevant result from the given participation
     * (relevancy depends on Exercise type => this should be overridden by subclasses if necessary)
     *
     * @param participation the participation whose results we are considering
     * @return the latest relevant result in the given participation, or null, if none exist
     */
    public Result findLatestRelevantResult(Participation participation) {
        // for most types of exercises => return latest result (all results are relevant)
        Result latestResult = null;
        for (Result result : participation.getResults()) {
            if (latestResult == null || latestResult.getCompletionDate().isBefore(result.getCompletionDate())) {
                latestResult = result;
            }
        }
        return latestResult;
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
            ", title='" + getTitle() + "'" +
            ", releaseDate='" + getReleaseDate() + "'" +
            ", dueDate='" + getDueDate() + "'" +
            ", maxScore=" + getMaxScore() +
            "}";
    }
}
