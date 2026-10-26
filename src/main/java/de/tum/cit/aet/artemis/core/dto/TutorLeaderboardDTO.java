package de.tum.cit.aet.artemis.core.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardDTO(long userId, String name, long numberOfAssessments, long numberOfAcceptedComplaints, long numberOfTutorComplaints,
        long numberOfNotAnsweredMoreFeedbackRequests, long numberOfComplaintResponses, long numberOfAnsweredMoreFeedbackRequests, long numberOfTutorMoreFeedbackRequests,
        double points, @Nullable Double averageScore, double averageRating, long numberOfTutorRatings) {

}
