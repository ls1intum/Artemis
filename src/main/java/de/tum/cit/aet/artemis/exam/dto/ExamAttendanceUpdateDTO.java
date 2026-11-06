package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** An instructor's attendance decision. A null value restores the automatic attendance status. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamAttendanceUpdateDTO(Boolean attended) {
}
