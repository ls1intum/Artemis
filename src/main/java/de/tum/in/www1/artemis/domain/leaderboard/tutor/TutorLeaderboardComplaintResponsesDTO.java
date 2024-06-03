package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardComplaintResponsesDTO(long userId, long complaintResponses, double points) {

    public TutorLeaderboardComplaintResponsesDTO(Long userId, Long complaintResponses, Double points) {
        this(userId == null ? 0 : userId, complaintResponses == null ? 0 : complaintResponses, points == null ? 0 : points);
    }

    public TutorLeaderboardComplaintResponsesDTO() {
        this(0L, 0L, 0.0);
    }
}
