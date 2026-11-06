package de.tum.cit.aet.artemis.exercise.service;

import java.io.Serializable;

/** Serializable cleanup intent containing resource identifiers only, never a bean, entity, or callback. */
public record ParticipationCleanupCommand(Operation operation, long participationId, long exerciseId, long submissionId, String projectKey, String buildPlanId,
        String repositoryUri) implements Serializable {

    public enum Operation {
        BUILD_PLAN, VC_REPOSITORY, LOCAL_REPOSITORY, QUEUED_JOBS, BUILD_LOG_FILES, COMPETENCY_PROGRESS
    }

    /** An ephemeral task for ordinary deletion; only its command may be retained in distributed storage. */
    public record Task(ParticipationCleanupCommand command, Runnable action) implements Runnable {

        @Override
        public void run() {
            action.run();
        }
    }
}
