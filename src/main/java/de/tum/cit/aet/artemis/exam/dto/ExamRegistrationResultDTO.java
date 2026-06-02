package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

public record ExamRegistrationResultDTO(List<ExamUserDTO> notFoundStudents, List<ExamUserDTO> rejectedStaffStudents) {

}
