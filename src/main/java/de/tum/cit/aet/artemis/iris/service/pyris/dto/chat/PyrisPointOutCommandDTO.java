package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A navigation command sent by Pyris alongside a chat result, telling Artemis to point the student to a page or video timestamp in the lecture combined view.
 *
 * @param lectureUnitId   the lecture unit the student is viewing
 * @param page            the 1-based slide page to display, or {@code null}
 * @param timestamp       the video position in seconds to seek to, or {@code null}
 * @param lectureUnitName the display name of the lecture unit (resolved by Artemis), or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PyrisPointOutCommandDTO(Long lectureUnitId, @Nullable Integer page, @Nullable Double timestamp, @Nullable String lectureUnitName) implements PyrisCommandDTO {

    @Override
    public String type() {
        return "pointOut";
    }
}
