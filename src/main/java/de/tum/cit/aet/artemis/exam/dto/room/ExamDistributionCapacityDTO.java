package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamDistributionCapacityDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int combinedDefaultCapacity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int combinedMaximumCapacity) {
}
