package de.tum.in.www1.artemis.domain.scores;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "student_score")
public class StudentScore extends DomainObject {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    /**
     * Last result of the student for the exercise no matter if the result is rated or not
     */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "last_result_id")
    private Result lastResult;

    /**
     * Last rated result of the student for the exercise
     */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "last_rated_result_id")
    private Result lastRatedResult;

    @Column(name = "last_score")
    private Long lastScore;

    @Column(name = "last_rated_score")
    private Long lastRatedScore;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Result getLastResult() {
        return lastResult;
    }

    public void setLastResult(Result lastResult) {
        this.lastResult = lastResult;
    }

    public Result getLastRatedResult() {
        return lastRatedResult;
    }

    public void setLastRatedResult(Result lastRatedResult) {
        this.lastRatedResult = lastRatedResult;
    }

    public Long getLastScore() {
        return lastScore;
    }

    public void setLastScore(Long lastScore) {
        this.lastScore = lastScore;
    }

    public Long getLastRatedScore() {
        return lastRatedScore;
    }

    public void setLastRatedScore(Long lastRatedScore) {
        this.lastRatedScore = lastRatedScore;
    }
}
