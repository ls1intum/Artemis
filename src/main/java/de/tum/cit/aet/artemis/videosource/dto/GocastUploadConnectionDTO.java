package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The active TUM.Live connection of a course, as uploads need it.
 *
 * @param integrationId the TUM.Live integration
 * @param grantId       the grant of the course
 * @param courseSlug    the TUM.Live course slug, for watch page URLs
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastUploadConnectionDTO(long integrationId, long grantId, String courseSlug) {
}
