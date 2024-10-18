package de.tum.cit.aet.artemis.atlas.domain.competency;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "competency_exercise")
public class CompetencyExerciseLink extends CompetencyLearningObjectLink {

    @ManyToOne(optional = false)
    @MapsId("learningObjectId")
    private Exercise exercise;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public CompetencyExerciseLink(CourseCompetency competency, Exercise exercise, double weight) {
        super(competency, weight);
        this.exercise = exercise;
    }

    public CompetencyExerciseLink() {
        // Empty constructor for Spring
    }

    @Override
    public String toString() {
        return "CompetencyExerciseLink{" + "exercise=" + exercise + ", id=" + id + ", competency=" + competency + ", weight=" + weight + '}';
    }
}
