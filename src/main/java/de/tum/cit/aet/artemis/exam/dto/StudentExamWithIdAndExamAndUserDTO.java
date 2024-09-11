package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.artemis.core.dto.UserWithIdAndLoginDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamWithIdAndExamAndUserDTO(long id, ExamWithIdAndCourseDTO exam, UserWithIdAndLoginDTO user) {
}
