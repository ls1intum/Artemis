package de.tum.in.www1.artemis.web.websocket.dto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

public class TeamAssignmentPayload {

    @NotNull
    private Exercise exercise;

    @Nullable
    private Team team;

    @NotNull
    private List<StudentParticipation> studentParticipations = List.of();

    public TeamAssignmentPayload(@NotNull Exercise exercise, @Nullable Team team) {
        this.exercise = exercise;
        this.team = team;
    }

    public TeamAssignmentPayload(@NotNull Exercise exercise, @Nullable Team team, @NotNull List<StudentParticipation> studentParticipations) {
        this(exercise, team);
        this.studentParticipations = studentParticipations;
    }

    public void setExercise(@NotNull Exercise exercise) {
        this.exercise = exercise;
    }

    public void setTeam(@Nullable Team team) {
        this.team = team;
    }

    public void setStudentParticipations(@NotNull List<StudentParticipation> studentParticipations) {
        this.studentParticipations = studentParticipations;
    }

    public long getExerciseId() {
        return this.exercise.getId();
    }

    public Long getTeamId() {
        return Optional.ofNullable(this.team).map(Team::getId).orElse(null);
    }

    public List<StudentParticipation> getStudentParticipations() {
        return this.studentParticipations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TeamAssignmentPayload that = (TeamAssignmentPayload) o;
        return exercise.equals(that.exercise) && Objects.equals(team, that.team) && studentParticipations.equals(that.studentParticipations);
    }
}
