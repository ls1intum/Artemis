package de.tum.cit.aet.artemis.videosource.domain;

/**
 * How a lecture unit's video is hosted. Drives (a) which download path Pyris
 * uses during transcription and (b) which player component the client renders.
 *
 * <p>
 * The Artemis server is the canonical source for this value; clients never
 * derive it from the raw URL.
 */
public enum VideoSourceType {
    TUM_LIVE, YOUTUBE
}
