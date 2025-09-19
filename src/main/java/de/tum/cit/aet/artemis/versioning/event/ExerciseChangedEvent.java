package de.tum.cit.aet.artemis.versioning.event;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * Spring application event indicating that an {@link Exercise} has been created or updated
 * and a version should be created after the surrounding transaction commits.
 *
 * @param exerciseId   the id of the exercise that has been created or updated
 * @param exerciseType the type of the exercise that has been created or updated {@link ExerciseType}
 * @param userLogin    the login of the user in security context
 */
public record ExerciseChangedEvent(Long exerciseId, ExerciseType exerciseType, String userLogin) {

}
