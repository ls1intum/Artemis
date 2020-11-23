package de.tum.in.www1.artemis.domain.scores;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "student_score")
public class StudentScore extends DomainObject {

    @ManyToOne
    private User student;

    @ManyToOne
    private Exercise exercise;

    /**
     * A submission can have a result and therefore, results are persisted and removed with a submission.
     */
    @OneToOne
    private Result result;

    @Column(name = "score")
    private long score;

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public StudentScore() {
        // Empty constructor because of @Entity
    }

    public StudentScore(User student, Exercise exercise, Result result) {
        this.student = student;
        this.exercise = exercise;
        this.result = result;
    }
}
