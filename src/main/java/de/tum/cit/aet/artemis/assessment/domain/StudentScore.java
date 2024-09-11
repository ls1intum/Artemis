package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;

@Entity
@DiscriminatorValue("SS")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
    public Participant getParticipant() {
        return getUser();
    }

    @Override
    public String toString() {
        Long id = getId();
        Long userId = getUser() != null ? getUser().getId() : null;
        Long exerciseId = getExercise() != null ? getExercise().getId() : null;
        Long lastResultId = getLastResult() != null ? getLastResult().getId() : null;
        Double lastResultScore = getLastScore();
        Long lastRatedResultId = getLastRatedResult() != null ? getLastRatedResult().getId() : null;
        Double lastRatedScore = getLastRatedScore();

        return "StudentScore{" + "id=" + id + ", userId=" + userId + ", exerciseId=" + exerciseId + ", lastResultId=" + lastResultId + ", lastResultScore=" + lastResultScore
                + ", lastRatedResultId=" + lastRatedResultId + ", lastRatedResultScore=" + lastRatedScore + '}';
    }
}
