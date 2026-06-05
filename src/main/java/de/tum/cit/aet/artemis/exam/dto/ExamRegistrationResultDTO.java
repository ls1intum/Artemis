package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamRegistrationResultDTO(List<ExamUserDTO> notFoundStudents, List<ExamUserDTO> rejectedStaffStudents) {
}
