package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Where a browser sends a recording for a TUM.Live lecture created for one upload.
 *
 * @param streamId  the TUM.Live lecture
 * @param uploadUrl the absolute one-use upload URL on TUM.Live; the URL itself is the credential, so it is never logged
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastUploadTargetDTO(long streamId, String uploadUrl) {

    @Override
    public String toString() {
        return "GocastUploadTargetDTO[streamId=" + streamId + ", uploadUrl=[REDACTED]]";
    }
}
