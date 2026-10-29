package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

/** Synchronous activity admission; failure must abort draft preparation before its database transaction commits. */
public record GenerationAdmittedEvent(GenerationStartedEvent run) {
}
