package de.tum.cit.aet.artemis.exam.domain;

public enum ExamType {

    REAL, TEST, TEST_WITH_SIMULATION;

    public boolean isTestExamType() {
        return this != REAL;
    }
}
