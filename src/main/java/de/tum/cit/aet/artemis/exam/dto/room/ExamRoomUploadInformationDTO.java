package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomUploadInformationDTO(@NotNull String uploadedFileName, int numberOfUploadedRooms, int numberOfUploadedSeats, @NotNull List<String> uploadedRoomNames) {
}
