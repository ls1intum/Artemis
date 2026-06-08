package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Context information for video content in lectures.
 * Provides information about which lecture unit video the user is watching and the current timestamp.
 * <p>
 * This context is NOT persisted in the database - it is only sent to Pyris to provide
 * more relevant and contextual responses based on what the user is currently viewing.
 *
 * @param type          the context type identifier (always "video")
 * @param lectureUnitId the ID of the lecture unit containing the video
 * @param timestamp     video timestamp in seconds (supports decimals like 125.5)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisVideoContextDTO(@JsonProperty("type") @NonNull String type, @NonNull Long lectureUnitId, @NonNull Double timestamp) implements IrisMessageContextDTO {

    /**
     * Convenience constructor that automatically sets the type to "video".
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param timestamp     video timestamp in seconds
     */
    public IrisVideoContextDTO(@NonNull Long lectureUnitId, @NonNull Double timestamp) {
        this("video", lectureUnitId, timestamp);
    }
}
