package de.tum.cit.aet.artemis.exam.domain;

public enum ExamMode {

    REAL, TEST, TEST_WITH_SIMULATION;

    public boolean isTestExamMode() {
        return this != REAL;
    }
}
