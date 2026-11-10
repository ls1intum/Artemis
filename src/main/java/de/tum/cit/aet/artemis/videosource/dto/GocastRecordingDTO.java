package de.tum.cit.aet.artemis.videosource.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The state of a TUM.Live lecture's combined recording.
 *
 * @param playlistUrl the signed playlist, valid for seven hours, or null while there is no recording. A bearer credential: never log it.
 * @param converting  whether a conversion is running
 * @param progress    the percentage of the running conversion
 * @param failed      whether the last conversion ended without a video
 * @param error       why it failed, in words for the uploader
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastRecordingDTO(@Nullable String playlistUrl, boolean converting, int progress, boolean failed, @Nullable String error) {

    /**
     * An uploaded recording is ready once its playlist exists and nothing converts or failed. An upload lecture has no live playlist, so this is the converted
     * upload.
     *
     * @return whether the recording can be shown
     */
    public boolean ready() {
        return playlistUrl != null && !converting && !failed;
    }

    @Override
    public String toString() {
        return "GocastRecordingDTO[playlistUrl=" + (playlistUrl == null ? null : "[REDACTED]") + ", converting=" + converting + ", progress=" + progress + ", failed=" + failed
                + ", error=" + error + "]";
    }
}
