package de.tum.in.www1.artemis.web.websocket.dto;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamAssignmentPayload(@NonNull Exercise exercise, @Nullable Team team, @NonNull List<StudentParticipation> studentParticipations) {

    public long getExerciseId() {
        return exercise.getId();
    }

    @Nullable
    public Long getTeamId() {
        return team != null ? team.getId() : null;
    }
}
