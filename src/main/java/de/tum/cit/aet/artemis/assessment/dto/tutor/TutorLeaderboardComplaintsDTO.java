package de.tum.cit.aet.artemis.assessment.dto.tutor;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query, we cannot use primitive types here, because otherwise Hibernate gets confused
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardComplaintsDTO(Long userId, Long allComplaints, Long acceptedComplaints, Double points) {

    public TutorLeaderboardComplaintsDTO() {
        this(0L, 0L, 0L, 0.0);
    }

    @Override
    @NotNull
    public Long userId() {
        return userId != null ? userId : 0L;
    }

    @Override
    @NotNull
    public Long allComplaints() {
        return allComplaints != null ? allComplaints : 0L;
    }

    @Override
    @NotNull
    public Long acceptedComplaints() {
        return acceptedComplaints != null ? acceptedComplaints : 0L;
    }

    @Override
    @NotNull
    public Double points() {
        return points != null ? points : 0.0;
    }
}
