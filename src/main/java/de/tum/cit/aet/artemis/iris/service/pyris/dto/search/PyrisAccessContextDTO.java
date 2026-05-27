package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resolved access context sent to Pyris with every search request.
 * Artemis computes which courses the user can access per role (following Florian's access rules
 * in GlobalSearchResource) and serializes the result as opaque ID lists.
 * Pyris applies these as Weaviate filters — it never implements access logic itself.
 * The {@code now} timestamp lets Pyris apply the same date-based visibility rules that
 * GlobalSearchResource enforces for lecture units, exercises, exams, and FAQs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisAccessContextDTO(@JsonProperty("courseIds") List<Long> courseIds, @JsonProperty("editorCourseIds") List<Long> editorCourseIds,
        @JsonProperty("taCourseIds") List<Long> taCourseIds, @JsonProperty("studentCourseIds") List<Long> studentCourseIds,
        @JsonProperty("staffCourseIds") List<Long> staffCourseIds, @JsonProperty("now") ZonedDateTime now) {
}
