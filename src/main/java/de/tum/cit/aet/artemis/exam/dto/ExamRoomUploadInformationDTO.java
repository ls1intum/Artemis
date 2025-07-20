package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomUploadInformationDTO(
    String uploadedFileName,
    String uploadDuration,
    Integer numberOfUploadedRooms,
    Integer numberOfUploadedSeats,
    List<String> uploadedRoomNames
) {}
// @formatter:on
