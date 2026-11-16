package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The TUM.Live upload state of a unit, for its editors.
 *
 * @param connected    whether the course is connected to TUM.Live, so uploads are possible
 * @param pendingRef   the reference of an upload that is not attached yet
 * @param pendingSince when it started
 * @param converting   whether TUM.Live converts it
 * @param progress     the conversion's percentage
 * @param attachedRef  the reference of the upload whose video the unit shows, if Artemis uploaded it; tells an attached upload from a dropped one
 * @param lastError    why the last upload was dropped, if it was
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveUploadStateDTO(boolean connected, @Nullable String pendingRef, @Nullable ZonedDateTime pendingSince, boolean converting, int progress,
        @Nullable String attachedRef, @Nullable String lastError) {
}
