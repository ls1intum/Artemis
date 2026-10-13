package de.tum.cit.aet.artemis.metis.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A tutorial group of a course, as the group window settings list it. Also the projection of the query that reads the groups of a course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisTutorialGroupDTO(long id, @NotNull String title) {
}
