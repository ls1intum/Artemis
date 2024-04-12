package de.tum.in.www1.artemis.web.websocket.dto;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamAssignmentPayload(@Nonnull Exercise exercise, @Nullable Team team, @Nonnull List<StudentParticipation> studentParticipations) {

    public long getExerciseId() {
        return exercise.getId();
    }

    @Nullable
    public Long getTeamId() {
        return team != null ? team.getId() : null;
    }
}
