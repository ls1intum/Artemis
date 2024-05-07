package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

/**
 * DTO to represent a submission timestamp.
 *
 * @param exerciseId          the id of the exercise
 * @param submissionTimestamp the submission timestamp
 * @param score               the score of the submission
 */
public record SubmissionTimestampDTO(long exerciseId, ZonedDateTime submissionTimestamp, Double score) {
}
