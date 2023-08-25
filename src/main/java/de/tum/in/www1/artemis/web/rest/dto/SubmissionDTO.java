package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.Submission;

public record SubmissionDTO(long id, ParticipationDTO participation) {

    public static SubmissionDTO of(Submission submission) {
        return new SubmissionDTO(submission.getId(), ParticipationDTO.of(submission.getParticipation()));
    }
}
