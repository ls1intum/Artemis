package de.tum.cit.aet.artemis.globalsearch.dto.searchableentity;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Snapshot of the data needed to upsert a course into the unified {@code SearchableEntities} Weaviate
 * collection. Courses are searchable by title, short name, and description. Admins can see all
 * courses; other users only see courses they are enrolled in.
 */
public record CourseSearchableEntityDTO(@NotNull Long courseId, @NotNull String title, @Nullable String shortName, @Nullable String description) {

    /**
     * Extracts all required data from a {@link Course} entity.
     *
     * @param course the course entity
     * @return the extracted data safe to use in an async context
     */
    public static CourseSearchableEntityDTO fromCourse(Course course) {
        return new CourseSearchableEntityDTO(course.getId(), course.getTitle(), course.getShortName(), course.getDescription());
    }

    /**
     * Produces the Weaviate property map for this course row.
     *
     * @return the property map keyed by {@link SearchableEntitySchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.COURSE);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, courseId);
        // Courses use their own ID as course_id for consistent filtering
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TITLE, title);
        if (shortName != null) {
            properties.put(SearchableEntitySchema.Properties.SHORT_NAME, shortName);
        }
        if (description != null) {
            properties.put(SearchableEntitySchema.Properties.DESCRIPTION, description);
        }
        return properties;
    }
}
