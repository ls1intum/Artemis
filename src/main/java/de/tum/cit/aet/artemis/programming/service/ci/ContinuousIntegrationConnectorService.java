package de.tum.cit.aet.artemis.programming.service.ci;

import java.util.List;
import java.util.Optional;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.CIBuildStatusDTO;

/**
 * Generic interface for CI connector services that handle continuous integration operations
 * through external microservices. This interface is completely stateless from Artemis' perspective.
 * The connector microservice handles ALL CI state management, build plan creation, cleanup, and lifecycle management.
 * Artemis simply triggers builds and queries results - everything else is transparent.
 */
public interface ContinuousIntegrationConnectorService {

    /**
     * Triggers a build for the given participation. This is the core stateless operation.
     * The connector will automatically handle everything needed:
     * - Create build plans/projects if they don't exist
     * - Configure repositories and permissions
     * - Trigger the actual build
     * - Handle cleanup when appropriate
     *
     * @param participation     the participation to build
     * @param commitHash        optional commit hash to build (null for latest)
     * @param triggeredByPushTo the repository type that triggered the build (null if manual)
     * @param buildScript       the build script content (e.g., pipeline.groovy for Jenkins)
     * @throws ContinuousIntegrationException if the trigger request fails
     */
    void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo, String buildScript)
            throws ContinuousIntegrationException;

    /**
     * Triggers a build for the given participation with default parameters.
     *
     * @param participation the participation to build
     * @param buildScript   the build script content
     * @throws ContinuousIntegrationException if the trigger request fails
     */
    default void triggerBuild(ProgrammingExerciseParticipation participation, String buildScript) throws ContinuousIntegrationException {
        triggerBuild(participation, null, null, buildScript);
    }

    /**
     * Gets the current build status for a participation.
     *
     * @param participation the participation to check
     * @return the build status information, empty if no builds exist
     */
    Optional<CIBuildStatusDTO> getBuildStatus(ProgrammingExerciseParticipation participation);

    /**
     * Gets build logs for a participation.
     *
     * @param participation the participation to get logs for
     * @param buildId       optional specific build ID (null for latest)
     * @return the build logs, empty if no builds exist
     */
    Optional<String> getBuildLogs(ProgrammingExerciseParticipation participation, String buildId);

    /**
     * Gets the health status of the CI connector.
     *
     * @return the connector health information
     */
    ConnectorHealth getHealth();

    /**
     * Gets the list of supported programming languages from the connector.
     *
     * @return list of supported language identifiers
     */
    List<String> getSupportedProgrammingLanguages();

    /**
     * Requests a default build script template from the connector for a specific language.
     *
     * @param programmingLanguage the programming language
     * @param exerciseType        the type of exercise (e.g., "basic", "advanced")
     * @return the default build script template, or empty if not supported
     */
    Optional<String> getDefaultBuildScriptTemplate(String programmingLanguage, String exerciseType);
}
