package de.tum.in.www1.artemis.domain.scores;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Team;

@Entity
@DiscriminatorValue("TS")
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
    public String toString() {
        Long id = getId();
        Long teamId = getTeam() != null ? getTeam().getId() : null;
        Long exerciseId = getExercise() != null ? getExercise().getId() : null;
        Long lastResultId = getLastResult() != null ? getLastResult().getId() : null;
        Long lastResultScore = getLastScore();
        Long lastRatedResultId = getLastRatedResult() != null ? getLastRatedResult().getId() : null;
        Long lastRatedScore = getLastRatedScore();

        return "TeamScore{" + "id=" + id + ", teamId=" + teamId + ", exerciseId=" + exerciseId + ", lastResultId=" + lastResultId + ", lastResultScore=" + lastResultScore
                + ", lastRatedResultId=" + lastRatedResultId + ", lastRatedResultScore=" + lastRatedScore + '}';
    }

}
