package de.tum.cit.aet.artemis.exercise.domain.event;

import java.util.Set;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Domain event published when a new exercise version is successfully created.
 * <p>
 * This event enables decoupled reactions to exercise versioning, such as updating
 * search indices or other external systems, without tightly coupling the versioning
 * service to these concerns.
 * <p>
 * {@code changedFields} carries the identifiers of the exercise-snapshot fields that differ from
 * the previous version (empty for the very first version, where there is nothing to diff against).
 * Consumers that only care about specific kinds of change — e.g. the Atlas auto-orchestration
 * recorder, which acts only on content-bearing edits — can filter on this set without re-diffing
 * the snapshots. Version creation itself is never suppressed: every consumer (search indexing,
 * review-thread sync, editor sync) still receives every event.
 *
 * @param exercise      the exercise for which a version was created
 * @param changedFields the identifiers of the snapshot fields that changed versus the previous version
 */
public record ExerciseVersionCreatedEvent(Exercise exercise, Set<String> changedFields) {
}
