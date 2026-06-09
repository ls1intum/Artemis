package de.tum.cit.aet.artemis.iris.dto;

import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Context information for fullscreen mode in lectures.
 * Indicates that the user is viewing a lecture unit in fullscreen mode and provides the unit ID
 * for scoping RAG search to that specific unit.
 * <p>
 * This context is NOT persisted in the database - it is only sent to Pyris to provide
 * more focused search results by filtering to a specific lecture unit.
 *
 * @param type          the context type identifier (always "fullscreen")
 * @param lectureUnitId the ID of the lecture unit being viewed in fullscreen mode
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisFullscreenContextDTO(@JsonProperty("type") @NonNull String type, @Positive @NonNull Long lectureUnitId) implements IrisMessageContextDTO {

    /**
     * Convenience constructor that automatically sets the type to "fullscreen".
     *
     * @param lectureUnitId the ID of the lecture unit
     */
    public IrisFullscreenContextDTO(@NonNull Long lectureUnitId) {
        this("fullscreen", lectureUnitId);
    }
}
