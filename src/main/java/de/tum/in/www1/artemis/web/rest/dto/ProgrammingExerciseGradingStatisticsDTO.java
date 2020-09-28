package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a dto for providing statistics for the programming exercise test cases.
 */
public class ProgrammingExerciseGradingStatisticsDTO {

    private Integer numTestCases;

    private Integer numParticipations;

    private List<TestCaseStats> testCaseStatsList = new ArrayList<>();

    public Integer getNumTestCases() {
        return numTestCases;
    }

    public void setNumTestCases(Integer numTestCases) {
        this.numTestCases = numTestCases;
    }

    public Integer getNumParticipations() {
        return numParticipations;
    }

    public void setNumParticipations(Integer numParticipations) {
        this.numParticipations = numParticipations;
    }

    public List<TestCaseStats> getTestCaseStatsList() {
        return testCaseStatsList;
    }

    public void addTestCaseStats(TestCaseStats testCaseStats) {
        testCaseStatsList.add(testCaseStats);
    }

    public static class TestCaseStats {

        private String testName;

        private Integer numPassed;

        private Integer numFailed;

        public TestCaseStats(String name, Integer passed, Integer failed) {
            this.testName = name;
            this.numPassed = passed;
            this.numFailed = failed;
        }

        public String getTestName() {
            return testName;
        }

        public Integer getNumPassed() {
            return numPassed;
        }

        public Integer getNumFailed() {
            return numFailed;
        }

    }
}
