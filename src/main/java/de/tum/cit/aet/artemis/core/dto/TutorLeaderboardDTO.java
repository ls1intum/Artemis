package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of the tutor leaderboard of an assessment dashboard. The numeric components are always serialized, including 0; an empty
 * name is omitted.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long userId, String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfAssessments, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfAcceptedComplaints,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfTutorComplaints,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfNotAnsweredMoreFeedbackRequests,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfComplaintResponses,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfAnsweredMoreFeedbackRequests,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfTutorMoreFeedbackRequests, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double points,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double averageScore, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double averageRating,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfTutorRatings) {

}
