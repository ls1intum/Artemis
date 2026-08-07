package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * How complete a retained candidate is as an account of what the run actually produced.
 * <p>
 * Stated rather than implied, because the retention is bounded on purpose: it is a diagnostic copy held in cluster memory, not an archive. A caller that reads these files must
 * be able to tell "this is everything the agent wrote" from "this is what fit", and neither of those is discoverable from the file list alone.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum ExerciseGenerationArtifactCompleteness {
    /** Every produced file was retained in full. */
    COMPLETE,
    /** Files or file content were dropped to stay inside the retention bounds, or a file was withheld because it tripped the secret-material policy. */
    PARTIAL
}
