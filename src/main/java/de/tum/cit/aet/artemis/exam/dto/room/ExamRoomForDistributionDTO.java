package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomForDistributionDTO(@NotNull Long id, @NotBlank String roomNumber, @Nullable String alternativeRoomNumber, @NotBlank String name,
        @Nullable String alternativeName, @NotBlank String building) {

    public static ExamRoomForDistributionDTO from(ExamRoom examRoom) {
        return new ExamRoomForDistributionDTO(examRoom.getId(), examRoom.getRoomNumber(), examRoom.getAlternativeRoomNumber(), examRoom.getName(), examRoom.getAlternativeName(),
                examRoom.getBuilding());
    }
}
