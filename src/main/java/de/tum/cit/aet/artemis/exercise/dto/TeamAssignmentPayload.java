package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

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
