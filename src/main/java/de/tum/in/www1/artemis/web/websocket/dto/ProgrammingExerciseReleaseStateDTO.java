package de.tum.in.www1.artemis.web.websocket.dto;

public class ProgrammingExerciseReleaseStateDTO {

    private boolean isReleased;

    private boolean hasStudentResult;

    private boolean testCasesChanged;

    public boolean isReleased() {
        return isReleased;
    }

    public void setReleased(boolean released) {
        isReleased = released;
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

    public ProgrammingExerciseReleaseStateDTO released(boolean releasedValue) {
        setReleased(releasedValue);
        return this;
    }

    public ProgrammingExerciseReleaseStateDTO studentResult(boolean hasStudentResultsValue) {
        hasStudentResult(hasStudentResultsValue);
        return this;
    }

    public ProgrammingExerciseReleaseStateDTO testCasesChanged(boolean testCasesChangedValue) {
        setTestCasesChanged(testCasesChangedValue);
        return this;
    }

}
