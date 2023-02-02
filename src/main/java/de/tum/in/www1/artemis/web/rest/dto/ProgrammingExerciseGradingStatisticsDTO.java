package de.tum.in.www1.artemis.web.rest.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Feedback;

/**
 * This is a dto for providing statistics for the programming exercise test cases & sca categories.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseGradingStatisticsDTO {

    // number of the participations with a result
    private Integer numParticipations;

    // statistics for each test case
    private Map<String, TestCaseStats> testCaseStatsMap = new HashMap<>();

    // statistics for each category
    private Map<String, Map<Integer, Integer>> categoryIssuesMap = new HashMap<>();

    public void setNumParticipations(Integer numParticipations) {
        this.numParticipations = numParticipations;
    }

    public Integer getNumParticipations() {
        return numParticipations;
    }

    public void setTestCaseStatsMap(Map<String, TestCaseStats> testCaseStatsMap) {
        this.testCaseStatsMap = testCaseStatsMap;
    }

    public Map<String, TestCaseStats> getTestCaseStatsMap() {
        return testCaseStatsMap;
    }

    public void setCategoryIssuesMap(Map<String, Map<Integer, Integer>> categoryIssuesMap) {
        this.categoryIssuesMap = categoryIssuesMap;
    }

    public Map<String, Map<Integer, Integer>> getCategoryIssuesMap() {
        return categoryIssuesMap;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
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

        /**
         * Updates the statistics accordingly for a positive or negative feedback.
         * @param feedback that should be considered in the statistics.
         */
        public void updateWithFeedback(final Feedback feedback) {
            if (Boolean.TRUE.equals(feedback.isPositive())) {
                numPassed++;
            }
            else {
                numFailed++;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            TestCaseStats that = (TestCaseStats) obj;
            return Objects.equals(numPassed, that.numPassed) && Objects.equals(numFailed, that.numFailed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(numPassed, numFailed);
        }
    }
}
