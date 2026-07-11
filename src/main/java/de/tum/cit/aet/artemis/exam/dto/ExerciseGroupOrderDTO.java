package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal exercise-group reference used to persist the order of exercise groups within an exam.
 * <p>
 * The client sends the ids of the exam's exercise groups in the desired order and receives the persisted order back as
 * the same id list. Only the id is carried on purpose: reordering mutates nothing on the exercises themselves, so the
 * client keeps its already-loaded, fully-detailed exercise groups (quiz questions, template/solution participations, ...)
 * and merely re-applies the confirmed order. Reloading the groups from the database instead would drop exactly that
 * detail, which is why this endpoint historically echoed the request body.
 *
 * @param id the id of the exercise group
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseGroupOrderDTO(Long id) {
}
