package de.tum.cit.aet.artemis.exam.dto.submit;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single exercise as sent by the exam hand-in. Only the database id (matched against the exercise instance loaded
 * from the DB, which continues to drive the {@code instanceof} type switch server-side) and the student participations
 * are bound.
 * <p>
 * {@code studentParticipations} is kept as a {@link List} (never a {@code Set}) so the server's exact
 * {@code studentParticipations.size() != 1 -> skip} semantics are preserved by construction.
 *
 * @param id                    the id of the exercise
 * @param studentParticipations the student's participations for this exercise (the server only acts when exactly one is present)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitExamExerciseDTO(Long id, List<SubmitExamParticipationDTO> studentParticipations) {
}
