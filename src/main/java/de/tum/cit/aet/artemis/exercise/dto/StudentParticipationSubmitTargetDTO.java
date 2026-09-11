package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;

/**
 * The participation a submission is about to be saved against, as far as saving it needs to know.
 * <p>
 * Read instead of the participation entity: {@code Participation.exercise} is a {@code @ManyToOne} and therefore eager,
 * as is the exercise's course and, for an exam exercise, its exercise group with that group's exam and the exam's
 * course. Loading the entity to write one submission read all of them, once per submission of every student.
 * <p>
 * Deliberately carries no submissions. A caller that needs those - file upload reads the previous file off them -
 * resolves the participation itself. {@code ParticipationService} turns this back into a participation for the two
 * things that still need an object: the submission's foreign key, and the participation the response reports.
 *
 * @param id                  the id of the participation
 * @param initializationState the state of the participation
 * @param initializationDate  when the participation was initialized
 * @param individualDueDate   the individual due date, null when the exercise-wide one applies
 * @param testRun             whether the participation is a test run, which is also practice mode for a course exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentParticipationSubmitTargetDTO(long id, InitializationState initializationState, ZonedDateTime initializationDate, ZonedDateTime individualDueDate,
        boolean testRun) {
}
