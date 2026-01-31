package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomDistributionRequestBodyDTO(List<Long> roomIds, Map<Long, String> examRoomAliases) {
}
