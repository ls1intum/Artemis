package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for TUM Live video upload response.
 *
 * @param success  whether the upload was successful
 * @param message  a message describing the result
 * @param error    error message if upload failed
 * @param videoUrl the URL to watch the video on TUM Live
 * @param embedUrl the embeddable URL for iframe integration
 * @param streamId the TUM Live stream ID
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveUploadResponseDTO(boolean success, String message, String error, String videoUrl, String embedUrl, Long streamId) {

    /**
     * Creates a successful response.
     *
     * @param message  the success message
     * @param videoUrl the URL to watch the video
     * @param embedUrl the embeddable URL
     * @param streamId the stream ID
     * @return a successful TumLiveUploadResponseDTO
     */
    public static TumLiveUploadResponseDTO success(String message, String videoUrl, String embedUrl, Long streamId) {
        return new TumLiveUploadResponseDTO(true, message, null, videoUrl, embedUrl, streamId);
    }

    /**
     * Creates a successful response without URLs (for backwards compatibility).
     *
     * @param message the success message
     * @return a successful TumLiveUploadResponseDTO
     */
    public static TumLiveUploadResponseDTO success(String message) {
        return new TumLiveUploadResponseDTO(true, message, null, null, null, null);
    }

    /**
     * Creates a failure response.
     *
     * @param error the error message
     * @return a failed TumLiveUploadResponseDTO
     */
    public static TumLiveUploadResponseDTO failure(String error) {
        return new TumLiveUploadResponseDTO(false, null, error, null, null, null);
    }
}
