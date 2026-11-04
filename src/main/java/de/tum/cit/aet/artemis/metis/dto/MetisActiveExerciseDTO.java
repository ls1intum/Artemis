package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A course programming exercise with an enabled comprehension check that the generation poller still looks at, because its due date, the latest individual due
 * date of one of its participations, the end of its default window or the end of one of its group windows lies within the horizon. A projection of
 * {@code MetisExerciseConfigRepository.findActiveExercises}; no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisActiveExerciseDTO(long exerciseId, long courseId, ZonedDateTime dueDate) {
}
