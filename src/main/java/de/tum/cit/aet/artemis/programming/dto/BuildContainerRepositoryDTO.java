package de.tum.cit.aet.artemis.programming.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * A repository that is checked out into a {@link BuildContainerDTO build container}.
 * <p>
 * A container only selects which of the exercise's repositories are provisioned into it; where a repository is checked
 * out remains configured per exercise via the checkout paths of its build config. Scoping the repositories per
 * container is what keeps trusted and untrusted code apart: a container that does not list the test repository never
 * receives the instructor's test files. A repository is selected by its type alone; {@link RepositoryType#AUXILIARY}
 * selects every auxiliary repository of the exercise.
 *
 * @param type the type of the repository
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildContainerRepositoryDTO(@NotNull RepositoryType type) {
}
