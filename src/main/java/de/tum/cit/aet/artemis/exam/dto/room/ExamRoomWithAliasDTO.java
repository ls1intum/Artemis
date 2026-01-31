package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

// Constructed and returned in a repository query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomWithAliasDTO(long id, String roomNumber, String alternativeRoomNumber, String name, String alternativeName, String building, String alias) {
}
