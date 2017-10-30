package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A Exercise.
 */
@Entity
@Table(name = "exercise")
@Inheritance(strategy= InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name="discriminator",
    discriminatorType=DiscriminatorType.STRING
)
@DiscriminatorValue(value="E")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProgrammingExercise.class),
    @JsonSubTypes.Type(value = ModelingExercise.class),
    @JsonSubTypes.Type(value = QuizExercise.class)
})
public abstract class Exercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "release_date")
    private ZonedDateTime releaseDate;

    @Column(name = "due_date")
    private ZonedDateTime dueDate;

    @OneToMany(mappedBy = "exercise")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Participation> participations = new HashSet<>();

    @ManyToOne
    private Course course;

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

    public Set<Participation> getParticipations() {
        return participations;
    }

    public Exercise participations(Set<Participation> participations) {
        this.participations = participations;
        return this;
    }

    public Exercise addParticipations(Participation participation) {
        this.participations.add(participation);
        participation.setExercise(this);
        return this;
    }

    public Exercise removeParticipations(Participation participation) {
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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
            "}";
    }
}
