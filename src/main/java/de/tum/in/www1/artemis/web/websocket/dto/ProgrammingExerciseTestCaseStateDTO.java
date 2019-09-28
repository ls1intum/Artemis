package de.tum.in.www1.artemis.web.websocket.dto;

/**
 * This DTO contains information that is valuable to determine the test case state of the programming exercise:
 * - isReleased: has the programming exercise's release date passed?
 * - hasStudentResult: is there at least one student submission with a result?
 * - testCasesChanged: have the test cases been changed after the exercise was released and a student result existed?
 */
public class ProgrammingExerciseTestCaseStateDTO {

    private boolean released;

    private boolean hasStudentResult;

    private boolean testCasesChanged;

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

}
