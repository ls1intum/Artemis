package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;

@Entity
@DiscriminatorValue("TS")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TeamScore extends ParticipantScore {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    @Override
    public Participant getParticipant() {
        return getTeam();
    }

    @Override
    public String toString() {
        Long id = getId();
        Long teamId = getTeam() != null ? getTeam().getId() : null;
        Long exerciseId = getExercise() != null ? getExercise().getId() : null;
        Long lastResultId = getLastResult() != null ? getLastResult().getId() : null;
        Double lastResultScore = getLastScore();
        Long lastRatedResultId = getLastRatedResult() != null ? getLastRatedResult().getId() : null;
        Double lastRatedScore = getLastRatedScore();

        return "TeamScore{" + "id=" + id + ", teamId=" + teamId + ", exerciseId=" + exerciseId + ", lastResultId=" + lastResultId + ", lastResultScore=" + lastResultScore
                + ", lastRatedResultId=" + lastRatedResultId + ", lastRatedResultScore=" + lastRatedScore + '}';
    }

}
