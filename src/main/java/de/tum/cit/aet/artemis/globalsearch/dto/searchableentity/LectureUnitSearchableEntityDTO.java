package de.tum.cit.aet.artemis.globalsearch.dto.searchableentity;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

/**
 * Snapshot of the data needed to upsert a lecture unit into the unified {@code SearchableItems}
 * Weaviate collection.
 * <p>
 * Only {@link TextUnit}, {@link OnlineUnit} and {@link AttachmentVideoUnit} are indexed; exercise
 * units are intentionally skipped because the underlying exercise is already indexed as
 * {@link SearchableEntitySchema.TypeValues#EXERCISE}.
 * <p>
 * Student visibility is determined at query time by filtering on {@code release_date} rather than
 * storing a pre-computed boolean, so that visibility changes take effect immediately without
 * re-indexing.
 */
public record LectureUnitSearchableEntityDTO(Long lectureUnitId, Long courseId, Long lectureId, String unitName, String description, String unitType, ZonedDateTime releaseDate) {

    /**
     * Extracts all required data from a supported {@link LectureUnit} subtype.
     *
     * @param unit the lecture unit entity (must have lecture + course relationships loaded, must be one of
     *                 {@link TextUnit}, {@link OnlineUnit}, {@link AttachmentVideoUnit})
     * @return the extracted data safe to use in an async context
     * @throws IllegalArgumentException                  if the unit type is not supported for indexing
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static LectureUnitSearchableEntityDTO fromLectureUnit(LectureUnit unit) {
        Lecture lecture = unit.getLecture();
        Long courseId = lecture.getCourse().getId();
        Long lectureId = lecture.getId();

        String description;
        String unitType;
        if (unit instanceof TextUnit textUnit) {
            description = textUnit.getContent();
            unitType = "text";
        }
        else if (unit instanceof OnlineUnit onlineUnit) {
            description = onlineUnit.getDescription();
            unitType = "online";
        }
        else if (unit instanceof AttachmentVideoUnit attachmentVideoUnit) {
            description = attachmentVideoUnit.getDescription();
            unitType = "attachment_video";
        }
        else {
            throw new IllegalArgumentException("Unsupported lecture unit type for Weaviate indexing: " + unit.getClass().getSimpleName());
        }

        return new LectureUnitSearchableEntityDTO(unit.getId(), courseId, lectureId, unit.getName(), description, unitType, unit.getReleaseDate());
    }

    /**
     * Returns {@code true} iff the supplied unit is one of the indexable subtypes. Use this as a guard
     * before calling {@link #fromLectureUnit(LectureUnit)} to skip exercise units silently.
     *
     * @param unit the lecture unit to test
     * @return whether the unit should be synchronized to Weaviate
     */
    public static boolean isIndexable(LectureUnit unit) {
        return unit instanceof TextUnit || unit instanceof OnlineUnit || unit instanceof AttachmentVideoUnit;
    }

    /**
     * Produces the Weaviate property map for this lecture unit row.
     *
     * @return the property map keyed by {@link SearchableEntitySchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.LECTURE_UNIT);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, lectureUnitId);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.LECTURE_ID, lectureId);
        properties.put(SearchableEntitySchema.Properties.TITLE, unitName);
        properties.put(SearchableEntitySchema.Properties.UNIT_TYPE, unitType);
        if (description != null) {
            properties.put(SearchableEntitySchema.Properties.DESCRIPTION, description);
        }
        if (releaseDate != null) {
            properties.put(SearchableEntitySchema.Properties.RELEASE_DATE, releaseDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        return properties;
    }
}
