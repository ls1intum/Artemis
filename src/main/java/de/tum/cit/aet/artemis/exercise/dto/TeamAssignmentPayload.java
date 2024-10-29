package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamAssignmentPayload(@NotNull Exercise exercise, @Nullable Team team, @NotNull List<StudentParticipation> studentParticipations) {

    public long getExerciseId() {
        return exercise.getId();
    }

    @Nullable
    public Long getTeamId() {
        return team != null ? team.getId() : null;
    }
}
