package de.tum.cit.aet.artemis.exam.domain;

public enum ExamMode {

    REAL, TEST, TEST_WITH_SUPERVISED_SESSION;

    public boolean isReal() {
        return this == REAL;
    }
}
