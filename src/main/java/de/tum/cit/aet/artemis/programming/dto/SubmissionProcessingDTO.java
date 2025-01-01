package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

public record SubmissionProcessingDTO(long exerciseId, long participationId, String commitHash, ZonedDateTime submissionDate, ZonedDateTime buildStartDate,
        ZonedDateTime estimatedCompletionDate) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
