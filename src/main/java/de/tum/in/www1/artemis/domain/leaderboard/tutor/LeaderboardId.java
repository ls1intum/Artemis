package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonInclude;

@Immutable
@Embeddable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LeaderboardId implements Serializable {

    @Column(name = "user_id")
    private long userId;

    @Column(name = "exercise_id")
    private long exerciseId;

    public LeaderboardId() {

    }

    public LeaderboardId(long userId, long exerciseId) {
        this.userId = userId;
        this.exerciseId = exerciseId;
    }

    public long getUserId() {
        return userId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LeaderboardId))
            return false;
        LeaderboardId that = (LeaderboardId) o;
        return Objects.equals(getUserId(), that.getUserId()) && Objects.equals(getExerciseId(), that.getExerciseId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserId(), getExerciseId());
    }
}
