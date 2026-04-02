package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.Map;

import de.tum.cit.aet.artemis.hyperion.dto.HyperionCodeGenerationEventDTO;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

public interface HyperionCodeGenerationEventPublisher {

    /**
     * Publishes job started event.
     */
    void started();

    /**
     * Publishes job progress event.
     *
     * @param iteration current iteration number
     */
    void progress(int iteration);

    /**
     * Publishes file updated event.
     *
     * @param path           file path
     * @param repositoryType repository type
     * @param iteration      generation iteration that produced the update
     */
    void fileUpdated(String path, RepositoryType repositoryType, int iteration);

    /**
     * Publishes new file event.
     *
     * @param path           file path
     * @param repositoryType repository type
     * @param iteration      generation iteration that produced the file
     */
    void newFile(String path, RepositoryType repositoryType, int iteration);

    /**
     * Publishes job completion event.
     *
     * @param completionStatus       terminal completion outcome
     * @param completionReason       machine-readable completion reason
     * @param completionReasonParams optional parameters for the completion reason
     * @param attempts               number of attempts
     * @param message                result message
     */
    void done(HyperionCodeGenerationEventDTO.CompletionStatus completionStatus, HyperionCodeGenerationEventDTO.CompletionReason completionReason,
            Map<String, String> completionReasonParams, int attempts, String message);

    /**
     * Publishes job error event.
     *
     * @param message error message
     */
    void error(String message);
}
