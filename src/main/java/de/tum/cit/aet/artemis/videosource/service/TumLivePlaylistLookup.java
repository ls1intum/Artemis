package de.tum.cit.aet.artemis.videosource.service;

import org.jspecify.annotations.Nullable;

/**
 * The result of asking the TUM Live API for the playlist of a watch page: the playlist URL when there is one, and otherwise why there is none.
 *
 * @param playlistUrl the HLS playlist URL; non-null only when {@code outcome == RESOLVED}
 * @param outcome     whether the playlist was found, and if not, why
 */
public record TumLivePlaylistLookup(@Nullable String playlistUrl, Outcome outcome) {

    /**
     * Why a lookup did or did not produce a playlist.
     */
    public enum Outcome {
        /** The API returned a playlist URL. */
        RESOLVED,
        /** The API refused access (HTTP 401 or 403): the course is not public. */
        NOT_ACCESSIBLE,
        /** The stream exists but has no playlist yet, or the API does not know it (HTTP 404): typically no recording is uploaded yet. */
        NOT_AVAILABLE,
        /** The API could not be reached, failed, or asked to try later (timeout, connection error, HTTP 5xx, 408, 429, or another unexpected 4xx). */
        TEMPORARILY_UNAVAILABLE,
        /** The URL is not a TUM Live watch page, or the TUM Live API is not configured. */
        NOT_APPLICABLE
    }

    static TumLivePlaylistLookup resolved(String playlistUrl) {
        return new TumLivePlaylistLookup(playlistUrl, Outcome.RESOLVED);
    }

    static TumLivePlaylistLookup failed(Outcome outcome) {
        return new TumLivePlaylistLookup(null, outcome);
    }
}
