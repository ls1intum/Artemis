package de.tum.cit.aet.artemis.programming.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for providing statistics for the programming exercise test cases & sca categories.
 *
 * @param numParticipations number of the participations with a result
 * @param testCaseStatsMap  statistics for each test case
 * @param categoryIssuesMap statistics for each category
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseGradingStatisticsDTO(int numParticipations, Map<String, TestCaseStats> testCaseStatsMap, Map<String, Map<Integer, Integer>> categoryIssuesMap) {

    public ProgrammingExerciseGradingStatisticsDTO {
        if (testCaseStatsMap == null) {
            testCaseStatsMap = new HashMap<>();
        }
        if (categoryIssuesMap == null) {
            categoryIssuesMap = new HashMap<>();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestCaseStats(int numPassed, int numFailed) {
    }
}
