package de.tum.cit.aet.artemis.exam.dto.room;

import javax.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

// Constructed and returned in a repository query
// Only used for server internals, never shared between server and client
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomWithAliasDTO(@NotNull Long id, @NotNull String roomNumber, @Nullable String alternativeRoomNumber, @NotNull String name, @Nullable String alternativeName,
        @NotNull String building, @Nullable String alias) {
}
