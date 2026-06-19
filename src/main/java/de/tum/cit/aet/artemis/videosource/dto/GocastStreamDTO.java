package de.tum.cit.aet.artemis.videosource.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a gocast (TUM Live) stream as returned by the integration endpoint EP8
 * ({@code GET /integration/courses/{courseId}/streams}).
 * <p>
 * JSON field names follow the proto-gateway naming convention (camelCase from proto snake_case).
 * Fields not present in the response are silently ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastStreamDTO(@JsonProperty("streamId") long streamId, @JsonProperty("name") String name, @JsonProperty("private") boolean isPrivate,
        @JsonProperty("start") Instant start, @JsonProperty("end") Instant end) {
}
