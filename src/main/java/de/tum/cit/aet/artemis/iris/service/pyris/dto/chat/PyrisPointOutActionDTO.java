package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A navigation action sent by Pyris alongside a chat result, telling Artemis to point the student to a specific position in the lecture combined view they are currently
 * looking at. Produced by the {@code show_in_combined_view} tool. Artemis persists it as a COMMAND message (a clickable marker in the chat history) and, if the combined view is
 * still open on the client, navigates to the given page / timestamp.
 *
 * @param lectureUnitId   the lecture unit the student is viewing
 * @param page            the 1-based slide page to display, or {@code null}
 * @param timestamp       the video position in seconds to seek to, or {@code null}
 * @param lectureUnitName the display name of the lecture unit (resolved by Artemis), or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PyrisPointOutActionDTO(Long lectureUnitId, @Nullable Integer page, @Nullable Double timestamp, @Nullable String lectureUnitName) {
}
