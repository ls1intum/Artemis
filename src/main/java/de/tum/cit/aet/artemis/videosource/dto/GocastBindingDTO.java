package de.tum.cit.aet.artemis.videosource.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.videosource.domain.GocastBindingConnectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastBindingDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean available,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) GocastBindingConnectionStatus status, Long courseId, String courseName, String courseSlug, String courseVisibility,
        Instant expiresAt, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean upstreamUnavailable) {
}
