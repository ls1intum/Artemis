package de.tum.in.www1.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a course's responsible user, i.e., a person to report misconduct to.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResponsibleUserDTO(String name, String email) {
}
