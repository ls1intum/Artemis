package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The figures of the course management detail view. Every component is nullable and omitted from the JSON when null; a present 0 is a
 * real value.
 *
 * @param currentPercentageAssessments   assessed share of the submissions to manually assessed exercises, in percent
 * @param currentAbsoluteAssessments     number of assessments of manually assessed exercises
 * @param currentMaxAssessments          number of submissions to manually assessed exercises
 * @param currentPercentageComplaints    answered share of the complaints, in percent; 0 while complaints are disabled
 * @param currentAbsoluteComplaints      number of answered complaints
 * @param currentMaxComplaints           number of complaints
 * @param currentPercentageMoreFeedbacks answered share of the more-feedback requests, in percent; 0 while they are disabled
 * @param currentAbsoluteMoreFeedbacks   number of answered more-feedback requests
 * @param currentMaxMoreFeedbacks        number of more-feedback requests
 * @param currentPercentageAverageScore  average score of the students, in percent
 * @param currentAbsoluteAverageScore    average points of the students
 * @param currentMaxAverageScore         reachable points, including presentation points
 * @param currentTotalLlmCostInEur       total cost of the course's LLM usage, in euros
 */
@Schema(description = "The figures of the course management detail view. A null figure is omitted; a present 0 is a real value.")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseManagementDetailViewDTO(Double currentPercentageAssessments, Long currentAbsoluteAssessments, Long currentMaxAssessments, Double currentPercentageComplaints,
        Long currentAbsoluteComplaints, Long currentMaxComplaints, Double currentPercentageMoreFeedbacks, Long currentAbsoluteMoreFeedbacks, Long currentMaxMoreFeedbacks,
        Double currentPercentageAverageScore, Double currentAbsoluteAverageScore, Double currentMaxAverageScore, Double currentTotalLlmCostInEur) {
}
