package de.tum.cit.aet.artemis.aiworker.api;

import java.util.UUID;

/** Worker-local sandbox lifecycle. This API is never exposed as a remote execution endpoint. */
public interface SandboxApi {

    /**
     * Reconciles the configured worker's orphaned containers before advertising readiness.
     *
     * @return the immutable sandbox image identifier
     */
    String prepare();

    /**
     * Scopes sandbox creation and cleanup to an admitted execution.
     *
     * @param executionId opaque execution identity, independent of any workload's domain identifiers
     * @return a local sandbox restricted to that execution
     */
    InteractiveSandbox forExecution(UUID executionId);

    /**
     * Removes only the named execution's containers, including a lost Docker create response.
     *
     * @param executionId the execution to clean up
     */
    void destroyExecution(UUID executionId);
}
