package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardComplaintsDTO(long userId, long allComplaints, long acceptedComplaints, double points) {

    public TutorLeaderboardComplaintsDTO(Long userId, Long allComplaints, Long acceptedComplaints, Double points) {
        this(userId == null ? 0 : userId, allComplaints == null ? 0 : allComplaints, acceptedComplaints == null ? 0 : acceptedComplaints, points == null ? 0 : points);
    }

    public TutorLeaderboardComplaintsDTO() {
        this(0L, 0L, 0L, 0.0);
    }
}
