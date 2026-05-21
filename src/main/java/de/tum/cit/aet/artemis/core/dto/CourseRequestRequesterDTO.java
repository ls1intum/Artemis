package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A lightweight DTO containing only the requester fields needed for course request display.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestRequesterDTO(Long id, String login, String name, String email) {

    public CourseRequestRequesterDTO(User user) {
        this(user.getId(), user.getLogin(), user.getName(), user.getEmail());
    }
}
