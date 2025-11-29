package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

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
     */
    void fileUpdated(String path, RepositoryType repositoryType);

    /**
     * Publishes new file event.
     *
     * @param path           file path
     * @param repositoryType repository type
     */
    void newFile(String path, RepositoryType repositoryType);

    /**
     * Publishes job completion event.
     *
     * @param success  whether the job succeeded
     * @param attempts number of attempts
     * @param message  result message
     */
    void done(boolean success, int attempts, String message);

    /**
     * Publishes job error event.
     *
     * @param message error message
     */
    void error(String message);
}
