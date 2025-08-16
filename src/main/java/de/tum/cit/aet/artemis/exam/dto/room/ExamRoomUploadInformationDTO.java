package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomUploadInformationDTO(
    @NotNull String uploadedFileName,
    @NotNull String uploadDuration,
    @NotNull Integer numberOfUploadedRooms,
    @NotNull Integer numberOfUploadedSeats,
    @NotNull List<String> uploadedRoomNames
) {}
// @formatter:on
