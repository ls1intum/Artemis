package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;

public record SubmissionDTO(Long id, Boolean submitted, SubmissionType type, Boolean exampleSubmission, ZonedDateTime submissionDate, String commitHash, Boolean buildFailed,
        Boolean buildArtifact, ParticipationIdDTO participation) {

    public record ParticipationIdDTO(Long id) {

        public ParticipationIdDTO(Participation participation) {
            this(participation.getId());
        }
    }
}
