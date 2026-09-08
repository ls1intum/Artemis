package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A named, independently executable container of a build plan. Each container runs its own Docker image, checks out
 * only the repositories it lists, and executes its build phases inside that image. Docker flags (network, CPU, memory,
 * environment variables) are configured per exercise and apply to every container of the build plan.
 * <p>
 * Scoping the repositories per container is what enforces isolation between trusted and untrusted code: a container
 * that runs student-authored tests can be provisioned with the assignment repository only, so that the instructor's
 * test files are never copied into it.
 *
 * @param name         the name of the container, unique within the build plan
 * @param dockerImage  the Docker image the container runs, or null to use the default image of the exercise
 * @param repositories the repositories checked out into the container, or null to check out the repositories
 *                         configured on the exercise, as a build plan without containers does
 * @param phases       the build phases executed inside the container, in order
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildContainerDTO(@NotBlank @Pattern(regexp = BuildContainerDTO.BUILD_CONTAINER_NAME_REGEX) String name, String dockerImage,
        List<@Valid BuildContainerRepositoryDTO> repositories, @NotEmpty List<@Valid BuildPhaseDTO> phases) {

    /**
     * Creates a container that checks out the repositories configured on the exercise, i.e. one that does not scope its
     * repositories.
     *
     * @param name        the name of the container
     * @param dockerImage the Docker image the container runs
     * @param phases      the build phases executed inside the container
     */
    public BuildContainerDTO(String name, String dockerImage, List<BuildPhaseDTO> phases) {
        this(name, dockerImage, null, phases);
    }

    public static final String BUILD_CONTAINER_NAME_REGEX = "^[A-Za-z_][A-Za-z0-9_]*$";

    public static final java.util.regex.Pattern BUILD_CONTAINER_NAME_PATTERN = java.util.regex.Pattern.compile(BUILD_CONTAINER_NAME_REGEX);

    /**
     * The name given to the container that a legacy build plan configuration, which only carries phases and a single
     * Docker image, is normalized into.
     */
    public static final String DEFAULT_CONTAINER_NAME = "default";
}
