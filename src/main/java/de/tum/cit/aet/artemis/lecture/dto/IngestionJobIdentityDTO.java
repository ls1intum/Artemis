package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Identity of an in-flight lecture ingestion job, resolved from its token.
 * Used by the iris module to reconstruct a callback job after the distributed job map entry expired.
 *
 * @param courseId      the ID of the course the lecture unit belongs to
 * @param lectureId     the ID of the lecture the unit belongs to
 * @param lectureUnitId the ID of the lecture unit being ingested
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IngestionJobIdentityDTO(long courseId, long lectureId, long lectureUnitId) {
}
