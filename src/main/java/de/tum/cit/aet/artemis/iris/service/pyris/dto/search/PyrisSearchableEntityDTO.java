package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Entity result pre-fetched by Artemis from Weaviate and forwarded to Pyris in the global-search
 * answer request. Matches the shape of Pyris's {@code GlobalSearchSourceDTO} so Pyris can use
 * these results directly in the RRF merge without querying Weaviate itself.
 * <p>
 * Course name is intentionally left blank — Pyris enriches it from the Artemis API after the merge.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisSearchableEntityDTO(@JsonProperty("sourceType") String sourceType, @JsonProperty("entityId") long entityId, @JsonProperty("course") CourseRef course,
        @JsonProperty("title") String title, @JsonProperty("snippet") @Nullable String snippet, @JsonProperty("exerciseType") @Nullable String exerciseType) {

    public record CourseRef(@JsonProperty("id") long id, @JsonProperty("name") String name) {
    }

    private static final java.util.Set<String> IRIS_ENTITY_TYPES = java.util.Set.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.FAQ,
            SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.CHANNEL);

    /**
     * Converts a raw Weaviate property map to a {@link PyrisSearchableEntityDTO}.
     * Returns {@code null} for entity types that Pyris does not handle in the answer pipeline
     * (lectures, lecture units, courses, posts) or if required fields are missing.
     */
    @Nullable
    public static PyrisSearchableEntityDTO fromProperties(Map<String, Object> props) {
        String type = getString(props, SearchableEntitySchema.Properties.TYPE);
        if (type == null || !IRIS_ENTITY_TYPES.contains(type)) {
            return null;
        }
        Long entityId = getLong(props, SearchableEntitySchema.Properties.ENTITY_ID);
        Long courseId = getLong(props, SearchableEntitySchema.Properties.COURSE_ID);
        String title = getString(props, SearchableEntitySchema.Properties.TITLE);
        if (entityId == null || courseId == null || title == null) {
            return null;
        }
        String description = getString(props, SearchableEntitySchema.Properties.DESCRIPTION);
        String exerciseType = SearchableEntitySchema.TypeValues.EXERCISE.equals(type) ? getString(props, SearchableEntitySchema.Properties.EXERCISE_TYPE) : null;
        String snippet = description != null ? description : fallbackDescription(type, title, exerciseType);
        return new PyrisSearchableEntityDTO(type, entityId, new CourseRef(courseId, ""), title, snippet, exerciseType);
    }

    /**
     * Returns a minimal factual description for entities with no description in Weaviate,
     * so the LLM can identify the entity type without inferring it from the entity name.
     * Add a case here when adding a new entity type to the Iris pipeline.
     */
    @Nullable
    private static String fallbackDescription(String type, String title, @Nullable String exerciseType) {
        return switch (type) {
            case SearchableEntitySchema.TypeValues.CHANNEL -> {
                if (title.startsWith("exercise-"))
                    yield "A communication channel for asking questions and discussing an exercise.";
                if (title.startsWith("exam-"))
                    yield "A communication channel for asking questions and discussing an exam.";
                if (title.startsWith("lecture-"))
                    yield "A communication channel for asking questions and discussing a lecture.";
                yield "A communication channel where students can ask questions and discuss.";
            }
            case SearchableEntitySchema.TypeValues.EXERCISE -> exerciseType != null ? "A " + exerciseType.toLowerCase() + " exercise: " + title : "An exercise: " + title;
            case SearchableEntitySchema.TypeValues.EXAM -> "An exam: " + title;
            case SearchableEntitySchema.TypeValues.FAQ -> "A frequently asked question: " + title;
            default -> null;
        };
    }

    private static @Nullable String getString(Map<String, Object> props, String key) {
        Object v = props.get(key);
        return v != null ? v.toString() : null;
    }

    private static @Nullable Long getLong(Map<String, Object> props, String key) {
        Object v = props.get(key);
        return v instanceof Number n ? n.longValue() : null;
    }
}
