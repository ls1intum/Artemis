package de.tum.cit.aet.artemis.globalsearch.dto.searchableitem;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableItemSchema;

/**
 * Snapshot of the data needed to upsert an exam into the unified {@code SearchableItems} Weaviate collection.
 * <p>
 * The {@link Exam} entity has no dedicated description column. {@code description} is composed from
 * {@link Exam#getStartText() startText} and {@link Exam#getEndText() endText} — the texts shown to
 * students before / after the exam — concatenated with a blank line separator. Both are typically
 * short or empty.
 */
public record ExamSearchableItemDTO(Long examId, Long courseId, String examTitle, String description, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate,
        boolean isTestExam) {

    /**
     * Extracts all required data from an {@link Exam} entity.
     *
     * @param exam the exam entity (must have course relationship loaded)
     * @return the extracted data safe to use in an async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static ExamSearchableItemDTO fromExam(Exam exam) {
        return new ExamSearchableItemDTO(exam.getId(), exam.getCourse().getId(), exam.getTitle(), buildDescription(exam), exam.getVisibleDate(), exam.getStartDate(),
                exam.getEndDate(), exam.isTestExam());
    }

    private static String buildDescription(Exam exam) {
        String start = exam.getStartText();
        String end = exam.getEndText();
        boolean hasStart = start != null && !start.isBlank();
        boolean hasEnd = end != null && !end.isBlank();
        if (hasStart && hasEnd) {
            return start + "\n\n" + end;
        }
        if (hasStart) {
            return start;
        }
        if (hasEnd) {
            return end;
        }
        return null;
    }

    /**
     * Produces the Weaviate property map for this exam row.
     *
     * @return the property map keyed by {@link SearchableItemSchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableItemSchema.Properties.TYPE, SearchableItemSchema.TypeValues.EXAM);
        properties.put(SearchableItemSchema.Properties.ENTITY_ID, examId);
        properties.put(SearchableItemSchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableItemSchema.Properties.TITLE, examTitle);
        properties.put(SearchableItemSchema.Properties.TEST_EXAM, isTestExam);
        if (description != null) {
            properties.put(SearchableItemSchema.Properties.DESCRIPTION, description);
        }
        if (visibleDate != null) {
            properties.put(SearchableItemSchema.Properties.VISIBLE_DATE, visibleDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        if (startDate != null) {
            properties.put(SearchableItemSchema.Properties.START_DATE, startDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        if (endDate != null) {
            properties.put(SearchableItemSchema.Properties.END_DATE, endDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        return properties;
    }
}
