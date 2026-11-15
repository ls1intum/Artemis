package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A unit that shows a video Artemis uploaded to TUM.Live, so deleting it or its lecture asks whether to delete the recording too.
 *
 * @param lectureId     the unit's lecture
 * @param lectureUnitId the unit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveUploadedUnitDTO(long lectureId, long lectureUnitId) {
}
