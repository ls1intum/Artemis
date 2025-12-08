package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for TUM Live video upload response.
 *
 * @param success whether the upload was successful
 * @param message a message describing the result
 * @param error   error message if upload failed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveUploadResponseDTO(boolean success, String message, String error) {

    /**
     * Creates a successful response.
     *
     * @param message the success message
     * @return a successful TumLiveUploadResponseDTO
     */
    public static TumLiveUploadResponseDTO success(String message) {
        return new TumLiveUploadResponseDTO(true, message, null);
    }

    /**
     * Creates a failure response.
     *
     * @param error the error message
     * @return a failed TumLiveUploadResponseDTO
     */
    public static TumLiveUploadResponseDTO failure(String error) {
        return new TumLiveUploadResponseDTO(false, null, error);
    }
}
