package de.tum.cit.aet.artemis.lecture.domain;

/**
 * Identifies the type of video source for transcription purposes.
 * The transcription service uses this to determine how to obtain the audio track.
 */
public enum VideoSourceType {

    /**
     * TUM Live video stream. The video URL is resolved to an HLS playlist URL via the TUM Live API.
     */
    TUM_LIVE,

    /**
     * YouTube video. The transcription service downloads the audio track directly using the YouTube video URL.
     */
    YOUTUBE
}
