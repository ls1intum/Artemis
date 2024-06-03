package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardDTO(long userId, String name, long numberOfAssessments, long numberOfAcceptedComplaints, long numberOfTutorComplaints,
        long numberOfNotAnsweredMoreFeedbackRequests, long numberOfComplaintResponses, long numberOfAnsweredMoreFeedbackRequests, long numberOfTutorMoreFeedbackRequests,
        double points, double averageScore, double averageRating, long numberOfTutorRatings) {

}
