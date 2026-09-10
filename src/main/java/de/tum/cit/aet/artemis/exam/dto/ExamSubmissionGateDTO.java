package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;

/**
 * A student's participation in an exam exercise, as far as saving a submission to it needs to know.
 * <p>
 * Read instead of the participation entity, which pulls its exercise and with it the course, the exercise group, the
 * exam and the exam's course, all eager to-one associations. The gate reads none of them: it needs the participation's
 * own columns, the student's name, and whether a submission already exists to overwrite. The caller holds the exercise.
 *
 * @param participationId      the id of the participation
 * @param existingSubmissionId the id of a submission that already exists for it, null if there is none
 * @param initializationState  the state of the participation
 * @param initializationDate   when the participation was initialized
 * @param individualDueDate    the individual due date of the participation, null if it has none
 * @param testRun              whether the participation is a test run
 * @param studentId            the id of the student
 * @param studentLogin         the login of the student
 * @param studentFirstName     the first name of the student
 * @param studentLastName      the last name of the student
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSubmissionGateDTO(long participationId, Long existingSubmissionId, InitializationState initializationState, ZonedDateTime initializationDate,
        ZonedDateTime individualDueDate, boolean testRun, long studentId, String studentLogin, String studentFirstName, String studentLastName) {
}
