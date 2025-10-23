package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseManagementDetailViewDTO(Double currentPercentageAssessments, Long currentAbsoluteAssessments, Long currentMaxAssessments, Double currentPercentageComplaints,
        Long currentAbsoluteComplaints, Long currentMaxComplaints, Double currentPercentageMoreFeedbacks, Long currentAbsoluteMoreFeedbacks, Long currentMaxMoreFeedbacks,
        Double currentPercentageAverageScore, Double currentAbsoluteAverageScore, Double currentMaxAverageScore, Double currentTotalLlmCostInEur) {
}
