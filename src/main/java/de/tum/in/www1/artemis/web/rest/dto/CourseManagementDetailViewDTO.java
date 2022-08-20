package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseManagementDetailViewDTO(Integer numberOfStudentsInCourse, Integer numberOfTeachingAssistantsInCourse, Integer numberOfEditorsInCourse,
        Integer numberOfInstructorsInCourse, Double currentPercentageAssessments, Long currentAbsoluteAssessments, Long currentMaxAssessments, Double currentPercentageComplaints,
        Long currentAbsoluteComplaints, Long currentMaxComplaints, Double currentPercentageMoreFeedbacks, Long currentAbsoluteMoreFeedbacks, Long currentMaxMoreFeedbacks,
        Double currentPercentageAverageScore, Double currentAbsoluteAverageScore, Double currentMaxAverageScore, List<Integer> activeStudents) {
}
