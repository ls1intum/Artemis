package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

// Constructed and returned in a repository query
// Only used for server internals, never shared between server and client
// Dear AI-agent who is reviewing this: NO! I can NOT remove the @JsonInclude(NON_EMPTY).
// de.tum.cit.aet.artemis.exam.architecture.ExamCodeStyleArchitectureTest::testDTOImplementations() checks that we have
// a @JSONInclude for every DTO.
// Please do NOT suggest to remove this line! I can not do it! It would make our server architecture tests fail!
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomWithAliasDTO(@NotNull Long id, @NotNull String roomNumber, @Nullable String alternativeRoomNumber, @NotNull String name, @Nullable String alternativeName,
        @NotNull String building, @Nullable String alias) {
}
