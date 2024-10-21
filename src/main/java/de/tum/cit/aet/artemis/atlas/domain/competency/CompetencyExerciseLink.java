package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

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
    protected CompetencyExerciseId id = new CompetencyExerciseId();

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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompetencyExerciseLink that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CompetencyExerciseLink{" + "exercise=" + exercise + ", id=" + id + ", competency=" + competency + ", weight=" + weight + '}';
    }

    @Embeddable
    public static class CompetencyExerciseId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private long exerciseId;

        private long competencyId;

        public CompetencyExerciseId() {
            // Empty constructor for Spring
        }

        public CompetencyExerciseId(long exerciseId, long competencyId) {
            this.exerciseId = exerciseId;
            this.competencyId = competencyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CompetencyExerciseId that)) {
                return false;
            }
            return exerciseId == that.exerciseId && competencyId == that.competencyId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(exerciseId, competencyId);
        }

        @Override
        public String toString() {
            return "CompetencyExerciseId{" + "exerciseId=" + exerciseId + ", competencyId=" + competencyId + '}';
        }
    }
}
