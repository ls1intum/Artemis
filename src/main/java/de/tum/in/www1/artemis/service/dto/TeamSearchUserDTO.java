package de.tum.in.www1.artemis.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;

/**
 * A DTO representing a user returned by searching for a student to add to a team.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamSearchUserDTO(Long id, @NotBlank @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = 1, max = 50) String login, @Size(max = 50) String name,
        @Size(max = 50) String firstName, @Size(max = 50) String lastName, @Size(max = 100) String email, Long assignedTeamId) {
}
