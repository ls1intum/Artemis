package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a gocast (TUM Live) course as returned by the integration endpoint EP1
 * ({@code GET /integration/users/{lrzId}/administered-courses}).
 * <p>
 * JSON field names follow the proto-gateway naming convention (camelCase from proto snake_case).
 * Fields not present in the response are silently ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GocastCourseDTO(@JsonProperty("id") long id, @JsonProperty("name") String name, @JsonProperty("slug") String slug, @JsonProperty("year") int year,
        @JsonProperty("teachingTerm") String teachingTerm, @JsonProperty("vodEnabled") boolean vodEnabled, @JsonProperty("visibility") String visibility) {
}
