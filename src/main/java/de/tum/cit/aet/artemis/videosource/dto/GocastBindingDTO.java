package de.tum.cit.aet.artemis.videosource.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.videosource.domain.GocastBindingConnectionStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastBindingDTO(boolean available, GocastBindingConnectionStatus status, Long courseId, String courseName, String courseSlug, String courseVisibility,
        Instant expiresAt, boolean upstreamUnavailable) {
}
