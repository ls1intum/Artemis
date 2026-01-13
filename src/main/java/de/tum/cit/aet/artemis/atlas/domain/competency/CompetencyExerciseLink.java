package de.tum.cit.aet.artemis.atlas.domain.competency;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "competency_exercise")
public class CompetencyExerciseLink extends CompetencyLearningObjectLink {

    @EmbeddedId
    @JsonIgnore
    protected CompetencyExerciseId id;

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @MapsId("exerciseId")
    private Exercise exercise;

    public CompetencyExerciseLink(CourseCompetency competency, Exercise exercise, double weight) {
        super(competency, weight);
        this.exercise = exercise;
    }

    public CompetencyExerciseLink() {
        // Empty constructor for Spring
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public CompetencyExerciseId getId() {
        return id;
    }

    @Override
    public String toString() {
        return "CompetencyExerciseLink{" + "exercise=" + exercise + ", id=" + id + ", competency=" + competency + ", weight=" + weight + '}';
    }

    @Embeddable
    public record CompetencyExerciseId(long exerciseId, long competencyId) {

    }
}
