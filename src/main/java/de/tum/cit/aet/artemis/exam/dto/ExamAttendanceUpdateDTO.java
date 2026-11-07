package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Confirms or clears the existing identity checks without supplying or removing a signature. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamAttendanceUpdateDTO(@NotNull Boolean attended) {
}
