package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomForDistributionDTO(long id, @NotBlank String roomNumber, @Nullable String alternativeRoomNumber, @NotBlank String name, @Nullable String alternativeName,
        @NotBlank String building) {
}
