package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * The participation a submission is about to be saved against, as far as saving it needs to know.
 * <p>
 * Read instead of the participation entity: {@code Participation.exercise} is a {@code @ManyToOne} and therefore eager,
 * as is the exercise's course and, for an exam exercise, its exercise group with that group's exam and the exam's
 * course. Loading the entity to write one submission read all of them, once per submission of every student.
 * <p>
 * The caller already holds the exercise and the participant, so {@link #toParticipation} can put the row back together
 * for the two things that still need an object: the submission's foreign key, and the participation the response
 * reports. It carries exactly the fields {@code StudentParticipationDTO} reads, and deliberately no submissions - a
 * caller that needs those resolves the participation itself.
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

    /**
     * Puts the row back together as a detached participation, with the exercise and participant the caller holds.
     *
     * @param exercise    the exercise the submission belongs to
     * @param participant the student or team the participation belongs to
     * @return a participation carrying the projected columns, the exercise and the participant
     */
    public StudentParticipation toParticipation(Exercise exercise, Object participant) {
        StudentParticipation participation = new StudentParticipation();
        participation.setId(id);
        participation.setInitializationState(initializationState);
        participation.setInitializationDate(initializationDate);
        participation.setIndividualDueDate(individualDueDate);
        participation.setTestRun(testRun);
        participation.setExercise(exercise);
        if (participant instanceof User user) {
            participation.setParticipant(user);
        }
        else if (participant instanceof Team team) {
            participation.setParticipant(team);
        }
        return participation;
    }
}
