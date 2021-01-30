package de.tum.in.www1.artemis.domain.scores;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.User;

@Entity
@DiscriminatorValue("SS")
public class StudentScore extends ParticipantScore {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        Long id = getId();
        Long userId = getUser() != null ? getUser().getId() : null;
        Long exerciseId = getExercise() != null ? getExercise().getId() : null;
        Long lastResultId = getLastResult() != null ? getLastResult().getId() : null;
        Long lastResultScore = getLastScore();
        Long lastRatedResultId = getLastRatedResult() != null ? getLastRatedResult().getId() : null;
        Long lastRatedScore = getLastRatedScore();

        return "StudentScore{" + "id=" + id + ", userId=" + userId + ", exerciseId=" + exerciseId + ", lastResultId=" + lastResultId + ", lastResultScore=" + lastResultScore
                + ", lastRatedResultId=" + lastRatedResultId + ", lastRatedResultScore=" + lastRatedScore + '}';
    }
}
