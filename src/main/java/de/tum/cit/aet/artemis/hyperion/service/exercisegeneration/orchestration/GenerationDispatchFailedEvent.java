package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

/** Terminal dispatch failure after a durable run identity may already have been committed. */
public record GenerationDispatchFailedEvent(String jobId) {
}
