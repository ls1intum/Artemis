package de.tum.in.www1.artemis.web.websocket.dto;

import java.util.Optional;

import javax.annotation.Nullable;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

public class TeamAssignmentPayload {

    private Exercise exercise;

    @Nullable
    private Team team;

    @Nullable
    private StudentParticipation participation;

    public TeamAssignmentPayload(Exercise exercise, @Nullable Team team, @Nullable StudentParticipation participation) {
        this.exercise = exercise;
        this.team = team;
        this.participation = participation;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public void setTeam(@Nullable Team team) {
        this.team = team;
    }

    public void setParticipation(@Nullable StudentParticipation participation) {
        this.participation = participation;
    }

    public long getExerciseId() {
        return this.exercise.getId();
    }

    public Long getTeamId() {
        return Optional.ofNullable(this.team).map(Team::getId).orElse(null);
    }

    public @Nullable StudentParticipation getParticipation() {
        return this.participation;
    }
}
