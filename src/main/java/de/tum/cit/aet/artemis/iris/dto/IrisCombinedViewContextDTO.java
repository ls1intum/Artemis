package de.tum.cit.aet.artemis.iris.dto;

import jakarta.validation.Valid;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Context information for the combined lecture view (fullscreen mode).
 * Indicates that the user is viewing a lecture unit in the combined view.
 * <p>
 * The combined view only exists when there is at least a slide or a video, so the slide context
 * (current PDF page) and/or video context (current timestamp) of the unit are carried directly on
 * this context instead of being sent as separate top-level context entries. Either nested context
 * may be absent (e.g. no PDF is open, or the video has not been played yet), but not both. The
 * lecture unit ID is derived from whichever nested context is present and is used to scope RAG
 * search to that specific unit.
 * <p>
 * This context is NOT persisted in the database - it is only sent to Pyris to provide
 * more focused search results by filtering to a specific lecture unit.
 *
 * @param type   the context type identifier (always "combinedView")
 * @param slides the slide context of the unit (current PDF page), or {@code null} if no slides are shown
 * @param video  the video context of the unit (current timestamp), or {@code null} if no video is playing
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedViewContextDTO(@JsonProperty("type") @NonNull String type, @Valid @Nullable IrisSlidesContextDTO slides, @Valid @Nullable IrisVideoContextDTO video)
        implements IrisMessageContextDTO {

    /**
     * Convenience constructor that automatically sets the type to "combinedView".
     *
     * @param slides the slide context of the unit, or {@code null} if no slides are shown
     * @param video  the video context of the unit, or {@code null} if no video is playing
     */
    public IrisCombinedViewContextDTO(@Nullable IrisSlidesContextDTO slides, @Nullable IrisVideoContextDTO video) {
        this("combinedView", slides, video);
    }

    /**
     * Derives the lecture unit ID from the nested slide or video context, used to scope RAG search.
     * Not serialized: the ID already lives on the nested contexts and must not be duplicated on the wire.
     *
     * @return the lecture unit ID, or {@code null} if neither a slide nor a video context is present
     */
    @JsonIgnore
    @Nullable
    public Long lectureUnitId() {
        if (slides != null) {
            return slides.lectureUnitId();
        }
        if (video != null) {
            return video.lectureUnitId();
        }
        return null;
    }
}
