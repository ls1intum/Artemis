package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This DTO contains information that is valuable to determine the test case state of the programming exercise:
 * - isReleased: has the programming exercise's release date passed?
 * - hasStudentResult: is there at least one student submission with a result?
 * - testCasesChanged: have the test cases been changed after the exercise was released and a student result existed?
 * - buildAndTestStudentSubmissionsAfterDueDate: Should the student submissions be triggered on this date after the due date to create rated results?
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseTestCaseStateDTO(boolean released, boolean hasStudentResult, boolean testCasesChanged, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate) {

}
