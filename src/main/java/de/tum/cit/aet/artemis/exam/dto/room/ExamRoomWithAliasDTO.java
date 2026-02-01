package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

// Constructed and returned in a repository query
// Only used for server internals, never shared between server and client
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomWithAliasDTO(long id, String roomNumber, String alternativeRoomNumber, String name, String alternativeName, String building, String alias) {
}
