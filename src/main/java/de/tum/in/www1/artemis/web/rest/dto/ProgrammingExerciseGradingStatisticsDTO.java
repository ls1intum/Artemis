package de.tum.in.www1.artemis.web.rest.dto;

import java.util.HashMap;

/**
 * This is a dto for providing statistics for the programming exercise test cases & sca categories.
 */
public class ProgrammingExerciseGradingStatisticsDTO {

    // number of the participations with a result
    private Integer numParticipations;
    // statistics for each test case
    private HashMap<String, TestCaseStats> testCaseStatsMap;
    // statistics for each category
    private HashMap<String, HashMap<Integer, Integer>> categoryIssuesMap;
    // highest number of issues a student has in one category
    private Integer maxIssuesPerCategory;

    public void setNumParticipations(Integer numParticipations) {
        this.numParticipations = numParticipations;
    }

    public Integer getNumParticipations() {
        return numParticipations;
    }

    public void setTestCaseStatsMap(HashMap<String, TestCaseStats> testCaseStatsMap) {
        this.testCaseStatsMap = testCaseStatsMap;
    }

    public HashMap<String, TestCaseStats> getTestCaseStatsMap() {
        return testCaseStatsMap;
    }

    public void setCategoryIssuesMap(HashMap<String, HashMap<Integer, Integer>> categoryIssuesMap) {
        this.categoryIssuesMap = categoryIssuesMap;
    }

    public HashMap<String, HashMap<Integer, Integer>> getCategoryIssuesMap() {
        return categoryIssuesMap;
    }

    public void setMaxIssuesPerCategory(Integer maxIssuesPerCategory) {
        this.maxIssuesPerCategory = maxIssuesPerCategory;
    }

    public int getMaxIssuesPerCategory() {
        return maxIssuesPerCategory;
    }

    public static class TestCaseStats {

        private Integer numPassed;

        private Integer numFailed;

        public TestCaseStats(Integer passed, Integer failed) {
            this.numPassed = passed;
            this.numFailed = failed;
        }

        public Integer getNumPassed() {
            return numPassed;
        }

        public Integer getNumFailed() {
            return numFailed;
        }

        public void increaseNumPassed() {
            numPassed++;
        }

        public void increaseNumFailed() {
            numFailed++;
        }
    }
}
