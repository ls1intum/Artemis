package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serial;
import java.io.Serializable;

public record SubmissionProcessingDTO(long exerciseId, long participationId, String commitHash) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
