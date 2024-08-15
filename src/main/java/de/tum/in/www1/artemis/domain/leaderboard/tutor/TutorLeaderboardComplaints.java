package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardComplaints(Long userId, Long allComplaints, Long acceptedComplaints, Double points) {

    public TutorLeaderboardComplaints() {
        this(0L, 0L, 0L, 0.0);
    }

    @Override
    @NonNull
    public Long userId() {
        return userId != null ? userId : 0L;
    }

    @Override
    @NonNull
    public Long allComplaints() {
        return allComplaints != null ? allComplaints : 0L;
    }

    @Override
    @NonNull
    public Long acceptedComplaints() {
        return acceptedComplaints != null ? acceptedComplaints : 0L;
    }

    @Override
    @NonNull
    public Double points() {
        return points != null ? points : 0.0;
    }
}
