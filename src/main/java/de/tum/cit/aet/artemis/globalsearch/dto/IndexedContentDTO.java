package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The objects stored in one Iris lecture-content Weaviate collection (slides, transcript, unit summaries, or aligned
 * segments) for a course, for the admin course-content browser.
 *
 * @param key     the stable content key (slides, transcript, unit_summary, segments) used for the frontend label
 * @param count   the number of objects returned (capped)
 * @param objects the stored objects, each with its ingestion time and full property map
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Objects stored in one Iris lecture-content collection for a course")
public record IndexedContentDTO(String key, int count, List<IndexedContentObjectDTO> objects) {

    /**
     * One object stored in an Iris content collection.
     *
     * @param ingestedAt the ISO-8601 time the object was created in Weaviate, or null when unavailable
     * @param properties the full property map stored in Weaviate for this object
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IndexedContentObjectDTO(@Nullable String ingestedAt, Map<String, Object> properties) {
    }
}
