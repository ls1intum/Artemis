package de.tum.in.www1.artemis.domain.hestia;

/**
 * Used to define the type of a ProgrammingExerciseTestCase.
 * Currently, only used for tests in Java exercises. Test cases in other languages are always of type DEFAULT
 */
public enum ProgrammingExerciseTestCaseType {
    /**
     * Test case has been generated by the structure oracle
     */
    STRUCTURAL,
    /**
     * Test case has been created by an instructor
     */
    BEHAVIORAL,
    /**
     * Type for all programming exercises not supported by Hestia
     */
    DEFAULT,
}
