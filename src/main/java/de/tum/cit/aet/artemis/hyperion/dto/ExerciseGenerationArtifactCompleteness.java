package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * How complete a retained candidate is as an account of what the run actually produced.
 * <p>
 * Retention is bounded — this is a diagnostic copy held in cluster memory, not an archive — and the file list alone does not tell a caller whether it is everything the agent
 * wrote or only what fit.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum ExerciseGenerationArtifactCompleteness {
    /** Every produced file was retained in full. */
    COMPLETE,
    /** Files or file content were dropped to stay inside the retention bounds, or a file was withheld because it tripped the secret-material policy. */
    PARTIAL
}
