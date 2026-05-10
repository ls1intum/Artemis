package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO holding participant identity data for export.
 * For individual exercises, {@code teamStudentNames} is null.
 * For team exercises, it contains the sorted list of member full names.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipationNameExportDTO(String participantName, String participantIdentifier, List<String> teamStudentNames) {
}
