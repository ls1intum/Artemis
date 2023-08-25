package de.tum.in.www1.artemis.web.rest.dto;

import java.time.Instant;

import de.tum.in.www1.artemis.domain.SubmissionVersion;

public record SubmissionVersionDTO(long id, Instant createdDate, String content, SubmissionDTO submission) {

    public static SubmissionVersionDTO of(SubmissionVersion submissionVersion) {
        return new SubmissionVersionDTO(submissionVersion.getId(), submissionVersion.getCreatedDate(), submissionVersion.getContent(),
                SubmissionDTO.of(submissionVersion.getSubmission()));
    }
}
