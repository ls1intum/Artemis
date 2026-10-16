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
 *
 * @param type the type of the repository
 * @param name the name of the auxiliary repository, only set for {@link RepositoryType#AUXILIARY}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildContainerRepositoryDTO(@NotNull RepositoryType type, String name) {

    /**
     * Creates a repository selection for a repository that is identified by its type alone, i.e. any repository except
     * an auxiliary one.
     *
     * @param type the type of the repository
     */
    public BuildContainerRepositoryDTO(RepositoryType type) {
        this(type, null);
    }
}
