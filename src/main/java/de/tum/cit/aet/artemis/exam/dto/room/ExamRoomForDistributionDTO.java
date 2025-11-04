package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomForDistributionDTO(long id, @NotBlank String roomNumber, @Nullable String alternativeRoomNumber, @NotBlank String name, @Nullable String alternativeName,
        @NotBlank String building) {
}
