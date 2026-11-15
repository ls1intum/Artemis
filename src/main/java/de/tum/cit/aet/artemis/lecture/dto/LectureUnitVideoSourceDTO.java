package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The video source of an attachment/video unit, with the course it belongs to. Lets a bulk query return the raw URL so
 * the caller can apply the supported-source rule, which cannot be expressed in JPQL.
 *
 * @param courseId    the id of the course the unit belongs to
 * @param unitId      the id of the unit
 * @param videoSource the unit's video URL
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitVideoSourceDTO(long courseId, long unitId, String videoSource) {
}
