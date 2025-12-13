package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for TUM Live authentication response.
 *
 * @param success whether authentication was successful
 * @param token   the JWT token for subsequent requests
 * @param expires the token expiration timestamp
 * @param user    information about the authenticated user
 * @param courses list of courses the user has access to
 * @param error   error message if authentication failed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveAuthResponseDTO(boolean success, String token, String expires, TumLiveUserDTO user, List<TumLiveCourseDTO> courses, String error) {

    /**
     * DTO for TUM Live user information.
     *
     * @param id    the user ID
     * @param name  the user's display name
     * @param email the user's email address
     * @param role  the user's role
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TumLiveUserDTO(Long id, String name, String email, String role) {
    }

    /**
     * DTO for TUM Live course information.
     *
     * @param id          the course ID
     * @param name        the course name
     * @param slug        the course slug for URL construction
     * @param year        the course year
     * @param term        the course term (e.g., "W" for winter, "S" for summer)
     * @param uploadUrl   the URL for uploading videos to this course
     * @param description the course description
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TumLiveCourseDTO(Long id, String name, String slug, Integer year, String term, String uploadUrl, String description) {
    }
}
