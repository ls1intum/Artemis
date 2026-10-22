package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastVerifiedCourseDTO(long integrationId, long grantId, long courseId, String courseSlug, String courseName, String courseVisibility) {
}
