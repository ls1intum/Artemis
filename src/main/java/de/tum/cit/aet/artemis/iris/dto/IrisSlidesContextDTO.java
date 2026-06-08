package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Context information for slide/PDF content in lectures.
 * Provides information about which lecture unit slides the user is viewing and the current page number.
 * <p>
 * This context is NOT persisted in the database - it is only sent to Pyris to provide
 * more relevant and contextual responses based on what the user is currently viewing.
 *
 * @param type          the context type identifier (always "slides")
 * @param lectureUnitId the ID of the lecture unit containing the slides/PDF
 * @param page          the current page number
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSlidesContextDTO(@JsonProperty("type") @NonNull String type, @NonNull Long lectureUnitId, @NonNull Integer page) implements IrisMessageContextDTO {

    /**
     * Convenience constructor that automatically sets the type to "slides".
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param page          the current page number
     */
    public IrisSlidesContextDTO(@NonNull Long lectureUnitId, @NonNull Integer page) {
        this("slides", lectureUnitId, page);
    }
}
