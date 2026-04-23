package de.tum.cit.aet.artemis.globalsearch.dto.searchableitem;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * Snapshot of the data needed to upsert a lecture into the unified {@code SearchableItems}
 * Weaviate collection.
 * <p>
 * Extracted before the async boundary so the Weaviate write runs outside the Hibernate session
 * without touching lazy relationships.
 */
public record LectureSearchableItemDTO(Long lectureId, Long courseId, String lectureTitle, String description, ZonedDateTime startDate, ZonedDateTime endDate) {

    /**
     * Extracts all required data from a {@link Lecture} entity.
     *
     * @param lecture the lecture entity (must have course relationship loaded)
     * @return the extracted data safe to use in an async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static LectureSearchableItemDTO fromLecture(Lecture lecture) {
        return new LectureSearchableItemDTO(lecture.getId(), lecture.getCourse().getId(), lecture.getTitle(), lecture.getDescription(), lecture.getStartDate(),
                lecture.getEndDate());
    }

    /**
     * Produces the Weaviate property map for this lecture row. Null optional fields are omitted.
     *
     * @return the property map keyed by {@link SearchableEntitySchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.LECTURE);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, lectureId);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TITLE, lectureTitle);
        if (description != null) {
            properties.put(SearchableEntitySchema.Properties.DESCRIPTION, description);
        }
        if (startDate != null) {
            properties.put(SearchableEntitySchema.Properties.START_DATE, startDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        if (endDate != null) {
            properties.put(SearchableEntitySchema.Properties.END_DATE, endDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        return properties;
    }
}
