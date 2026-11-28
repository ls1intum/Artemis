package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A named, independently executable container of a build plan. Each container runs its own Docker image, checks out
 * only the repositories it lists, and executes its build phases inside that image. The Docker flags of the exercise
 * (network, CPU, memory, environment variables) apply to every container; a container may override them for its own
 * job, see {@link BuildContainerDockerFlagsDTO}.
 * <p>
 * Scoping the repositories per container is what enforces isolation between trusted and untrusted code: a container
 * that runs student-authored tests can be provisioned with the assignment repository only, so that the instructor's
 * test files are never copied into it.
 * <p>
 * The name, repositories, phases and timeout carry no bean validation constraints: {@code BuildPlanConfigurationValidator}
 * checks them on every path that saves a build plan and names the container in its error, which a constraint violation
 * on the build plan endpoint would replace by a generic validation error.
 *
 * @param name           the name of the container, unique within the build plan
 * @param dockerImage    the Docker image the container runs, or null to use the default image of the exercise
 * @param repositories   the repositories checked out into the container. Null means the container is not scoped and checks
 *                           out the repositories configured on the exercise, as a build plan without containers does; an
 *                           empty list means the container is scoped and receives only the assignment repository. The
 *                           property is therefore serialized with Jackson's default inclusion (the bare annotation
 *                           overrides the record's NON_EMPTY): the empty list is kept, so that this "scoped to nothing"
 *                           state is not silently turned back into the unscoped state, and an unscoped container is
 *                           written with an explicit null, which the client reads like an absent property.
 * @param phases         the build phases executed inside the container, in order
 * @param timeoutSeconds the timeout of this container's build job in seconds, or null to use the timeout configured on
 *                           the exercise. The exercise timeout has to cover the slowest container; a container whose
 *                           phases are short can be bounded more tightly here, so that a hung or looping submission
 *                           frees its build agent slot, and the merged result reaches the student, sooner.
 * @param dockerFlags    the Docker flags this container sets for its own job, or null to use the exercise's flags
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildContainerDTO(String name, String dockerImage, @JsonInclude List<BuildContainerRepositoryDTO> repositories, List<BuildPhaseDTO> phases,
        @Nullable Integer timeoutSeconds, @Valid BuildContainerDockerFlagsDTO dockerFlags) {

    /**
     * Creates a container that checks out the repositories configured on the exercise, i.e. one that does not scope its
     * repositories, and uses the exercise's timeout and Docker flags.
     *
     * @param name        the name of the container
     * @param dockerImage the Docker image the container runs
     * @param phases      the build phases executed inside the container
     */
    public BuildContainerDTO(String name, String dockerImage, List<BuildPhaseDTO> phases) {
        this(name, dockerImage, null, phases, null, null);
    }

    /**
     * Creates a container that uses the exercise's timeout and Docker flags.
     *
     * @param name         the name of the container
     * @param dockerImage  the Docker image the container runs
     * @param repositories the repositories checked out into the container, see the record documentation
     * @param phases       the build phases executed inside the container
     */
    public BuildContainerDTO(String name, String dockerImage, List<BuildContainerRepositoryDTO> repositories, List<BuildPhaseDTO> phases) {
        this(name, dockerImage, repositories, phases, null, null);
    }

    /**
     * Creates a container with a timeout of its own that uses the exercise's Docker flags.
     *
     * @param name           the name of the container
     * @param dockerImage    the Docker image the container runs
     * @param repositories   the repositories checked out into the container, see the record documentation
     * @param phases         the build phases executed inside the container
     * @param timeoutSeconds the timeout of the container's build job, see the record documentation
     */
    public BuildContainerDTO(String name, String dockerImage, List<BuildContainerRepositoryDTO> repositories, List<BuildPhaseDTO> phases, Integer timeoutSeconds) {
        this(name, dockerImage, repositories, phases, timeoutSeconds, null);
    }

    /**
     * Creates a container with Docker flags of its own that uses the exercise's timeout.
     *
     * @param name         the name of the container
     * @param dockerImage  the Docker image the container runs
     * @param repositories the repositories checked out into the container, see the record documentation
     * @param phases       the build phases executed inside the container
     * @param dockerFlags  the Docker flags the container sets for its own job, see {@link BuildContainerDockerFlagsDTO}
     */
    public BuildContainerDTO(String name, String dockerImage, List<BuildContainerRepositoryDTO> repositories, List<BuildPhaseDTO> phases,
            BuildContainerDockerFlagsDTO dockerFlags) {
        this(name, dockerImage, repositories, phases, null, dockerFlags);
    }

    private static final String BUILD_CONTAINER_NAME_REGEX = "^[A-Za-z_][A-Za-z0-9_]*$";

    public static final Pattern BUILD_CONTAINER_NAME_PATTERN = Pattern.compile(BUILD_CONTAINER_NAME_REGEX);

    /**
     * The name given to the container that a legacy build plan configuration, which only carries phases and a single
     * Docker image, is normalized into.
     */
    public static final String DEFAULT_CONTAINER_NAME = "default";
}
