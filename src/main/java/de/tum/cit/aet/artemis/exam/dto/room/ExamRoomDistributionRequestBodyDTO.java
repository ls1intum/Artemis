package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomDistributionRequestBodyDTO(@Nullable List<Long> roomIds, @Nullable Map<Long, String> examRoomAliases) {
}
