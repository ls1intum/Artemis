package de.tum.in.www1.artemis.web.websocket.dto;

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
public class ProgrammingExerciseTestCaseStateDTO {

    private boolean released;

    private boolean hasStudentResult;

    private boolean testCasesChanged;

    private ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate;

    public boolean isReleased() {
        return released;
    }

    public void setReleased(boolean released) {
        this.released = released;
    }

    public boolean isHasStudentResult() {
        return hasStudentResult;
    }

    public void hasStudentResult(boolean hasStudentResult) {
        this.hasStudentResult = hasStudentResult;
    }

    public boolean isTestCasesChanged() {
        return testCasesChanged;
    }

    public void setTestCasesChanged(boolean testCasesChanged) {
        this.testCasesChanged = testCasesChanged;
    }

    public void setHasStudentResult(boolean hasStudentResult) {
        this.hasStudentResult = hasStudentResult;
    }

    public ZonedDateTime getBuildAndTestStudentSubmissionsAfterDueDate() {
        return buildAndTestStudentSubmissionsAfterDueDate;
    }

    public void setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate) {
        this.buildAndTestStudentSubmissionsAfterDueDate = buildAndTestStudentSubmissionsAfterDueDate;
    }

    public ProgrammingExerciseTestCaseStateDTO released(boolean releasedValue) {
        setReleased(releasedValue);
        return this;
    }

    public ProgrammingExerciseTestCaseStateDTO studentResult(boolean hasStudentResultsValue) {
        hasStudentResult(hasStudentResultsValue);
        return this;
    }

    public ProgrammingExerciseTestCaseStateDTO testCasesChanged(boolean testCasesChangedValue) {
        setTestCasesChanged(testCasesChangedValue);
        return this;
    }

    public ProgrammingExerciseTestCaseStateDTO buildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate) {
        setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestStudentSubmissionsAfterDueDate);
        return this;
    }
}
