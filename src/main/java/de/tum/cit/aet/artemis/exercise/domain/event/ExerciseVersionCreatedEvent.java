package de.tum.cit.aet.artemis.exercise.domain.event;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Domain event published when a new exercise version is successfully created.
 * <p>
 * This event enables decoupled reactions to exercise versioning, such as updating
 * search indices or other external systems, without tightly coupling the versioning
 * service to these concerns.
 *
 * @param exercise the exercise for which a version was created
 */
public record ExerciseVersionCreatedEvent(Exercise exercise) {
}
