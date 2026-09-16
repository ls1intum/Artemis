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
 * receives the instructor's test files.
 * <p>
 * A repository is identified by its type alone, so {@link RepositoryType#AUXILIARY} selects every auxiliary
 * repository of the exercise at once. Selecting a single auxiliary repository would need a name here, which is left
 * to future work; the type is wrapped in a record rather than listed bare so such a name can be added without
 * changing the shape of a stored build plan.
 *
 * @param type the type of the repository
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildContainerRepositoryDTO(@NotNull RepositoryType type) {
}
