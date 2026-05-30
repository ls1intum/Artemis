package de.tum.cit.aet.artemis.exam.domain;

public enum ExamType {

    REAL, SIMULATION, PRACTICE, SIMULATION_AND_PRACTICE;

    public boolean isTestExam() {
        return this != REAL;
    }
}
