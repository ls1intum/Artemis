package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Unified DTO for global search results across all entity types.
 * <p>
 * Provides a consistent structure for search results regardless of the underlying entity type
 * (exercises, lectures, lecture units, exams, FAQs, channels), which keeps the client-side
 * renderer simple.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Unified search result representing an entity found via global search")
public record GlobalSearchResultDTO(@Schema(description = "Unique identifier of the entity") String id,
        @Schema(description = "Entity type, e.g. 'exercise', 'lecture', 'exam'") String type, @Schema(description = "Display title of the entity") String title,
        @Schema(description = "Short description or body text excerpt") String description,
        @Schema(description = "Human-readable badge label, e.g. 'Programming', 'Quiz', 'Lecture'") String badge,
        @Schema(description = "Additional type-specific metadata such as courseId, dueDate, or points") Map<String, Object> metadata) {

    /**
     * Creates a search result DTO from a raw Weaviate property map returned by the unified
     * {@code SearchableItems} collection. Dispatches on the {@code type} discriminator to decide
     * which type-specific metadata to surface to the client.
     *
     * @param properties     the property map returned by Weaviate
     * @param courseNameById the batched map of course id → course title, resolved by the caller via
     *                           one {@code courseRepository.findAllById(...)} lookup per search request
     * @return the unified search result DTO, or {@code null} if the type discriminator is missing or unknown
     */
    public static GlobalSearchResultDTO fromSearchableItemProperties(Map<String, Object> properties, Map<Long, String> courseNameById) {
        String type = getString(properties, SearchableEntitySchema.Properties.TYPE);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SearchableEntitySchema.TypeValues.EXERCISE -> fromExerciseRow(properties, courseNameById);
            case SearchableEntitySchema.TypeValues.LECTURE -> fromLectureRow(properties, courseNameById);
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> fromLectureUnitRow(properties, courseNameById);
            case SearchableEntitySchema.TypeValues.EXAM -> fromExamRow(properties, courseNameById);
            case SearchableEntitySchema.TypeValues.FAQ -> fromFaqRow(properties, courseNameById);
            case SearchableEntitySchema.TypeValues.CHANNEL -> fromChannelRow(properties, courseNameById);
            default -> null;
        };
    }

    private static GlobalSearchResultDTO fromExerciseRow(Map<String, Object> properties, Map<Long, String> courseNameById) {
        String exerciseType = getString(properties, SearchableEntitySchema.Properties.EXERCISE_TYPE);
        String badge = formatExerciseTypeBadge(exerciseType);
        String title = getString(properties, SearchableEntitySchema.Properties.TITLE);
        String description = getString(properties, SearchableEntitySchema.Properties.DESCRIPTION);

        Map<String, Object> metadata = new HashMap<>();
        addCourseContext(properties, metadata, courseNameById);
        putIfNotNull(metadata, "dueDate", getString(properties, SearchableEntitySchema.Properties.DUE_DATE));
        putIfNotNull(metadata, "releaseDate", getString(properties, SearchableEntitySchema.Properties.RELEASE_DATE));
        putIfNotNull(metadata, "points", getDouble(properties, SearchableEntitySchema.Properties.MAX_POINTS));
        putIfNotNull(metadata, "difficulty", getString(properties, SearchableEntitySchema.Properties.DIFFICULTY));

        if ("programming".equals(exerciseType)) {
            putIfNotNull(metadata, "programmingLanguage", getString(properties, SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE));
        }
        else if ("modeling".equals(exerciseType)) {
            putIfNotNull(metadata, "diagramType", getString(properties, SearchableEntitySchema.Properties.DIAGRAM_TYPE));
        }
        else if ("quiz".equals(exerciseType)) {
            putIfNotNull(metadata, "quizDuration", getInteger(properties, SearchableEntitySchema.Properties.QUIZ_DURATION));
        }

        return new GlobalSearchResultDTO(idOrNull(properties), SearchableEntitySchema.TypeValues.EXERCISE, title, description, badge, metadata);
    }

    private static GlobalSearchResultDTO fromLectureRow(Map<String, Object> properties, Map<Long, String> courseNameById) {
        Map<String, Object> metadata = new HashMap<>();
        addCourseContext(properties, metadata, courseNameById);
        putIfNotNull(metadata, "startDate", getString(properties, SearchableEntitySchema.Properties.START_DATE));
        putIfNotNull(metadata, "endDate", getString(properties, SearchableEntitySchema.Properties.END_DATE));

        return new GlobalSearchResultDTO(idOrNull(properties), SearchableEntitySchema.TypeValues.LECTURE, getString(properties, SearchableEntitySchema.Properties.TITLE),
                getString(properties, SearchableEntitySchema.Properties.DESCRIPTION), "Lecture", metadata);
    }

    private static GlobalSearchResultDTO fromLectureUnitRow(Map<String, Object> properties, Map<Long, String> courseNameById) {
        Map<String, Object> metadata = new HashMap<>();
        addCourseContext(properties, metadata, courseNameById);
        Long lectureId = getLong(properties, SearchableEntitySchema.Properties.LECTURE_ID);
        if (lectureId != null) {
            metadata.put("lectureId", lectureId);
        }
        putIfNotNull(metadata, "unitType", getString(properties, SearchableEntitySchema.Properties.UNIT_TYPE));
        putIfNotNull(metadata, "releaseDate", getString(properties, SearchableEntitySchema.Properties.RELEASE_DATE));

        return new GlobalSearchResultDTO(idOrNull(properties), SearchableEntitySchema.TypeValues.LECTURE_UNIT, getString(properties, SearchableEntitySchema.Properties.TITLE),
                getString(properties, SearchableEntitySchema.Properties.DESCRIPTION), "Lecture Unit", metadata);
    }

    private static GlobalSearchResultDTO fromExamRow(Map<String, Object> properties, Map<Long, String> courseNameById) {
        Map<String, Object> metadata = new HashMap<>();
        addCourseContext(properties, metadata, courseNameById);
        putIfNotNull(metadata, "visibleDate", getString(properties, SearchableEntitySchema.Properties.VISIBLE_DATE));
        putIfNotNull(metadata, "startDate", getString(properties, SearchableEntitySchema.Properties.START_DATE));
        putIfNotNull(metadata, "endDate", getString(properties, SearchableEntitySchema.Properties.END_DATE));
        Boolean testExam = getBoolean(properties, SearchableEntitySchema.Properties.TEST_EXAM);
        if (testExam != null) {
            metadata.put("testExam", testExam);
        }

        String badge = Boolean.TRUE.equals(testExam) ? "Test Exam" : "Exam";
        return new GlobalSearchResultDTO(idOrNull(properties), SearchableEntitySchema.TypeValues.EXAM, getString(properties, SearchableEntitySchema.Properties.TITLE),
                getString(properties, SearchableEntitySchema.Properties.DESCRIPTION), badge, metadata);
    }

    private static GlobalSearchResultDTO fromFaqRow(Map<String, Object> properties, Map<Long, String> courseNameById) {
        Map<String, Object> metadata = new HashMap<>();
        addCourseContext(properties, metadata, courseNameById);
        putIfNotNull(metadata, "faqState", getString(properties, SearchableEntitySchema.Properties.FAQ_STATE));

        return new GlobalSearchResultDTO(idOrNull(properties), SearchableEntitySchema.TypeValues.FAQ, getString(properties, SearchableEntitySchema.Properties.TITLE),
                getString(properties, SearchableEntitySchema.Properties.DESCRIPTION), "FAQ", metadata);
    }

    private static GlobalSearchResultDTO fromChannelRow(Map<String, Object> properties, Map<Long, String> courseNameById) {
        Map<String, Object> metadata = new HashMap<>();
        addCourseContext(properties, metadata, courseNameById);
        Boolean isCourseWide = getBoolean(properties, SearchableEntitySchema.Properties.CHANNEL_IS_COURSE_WIDE);
        Boolean isPublic = getBoolean(properties, SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC);
        Boolean isArchived = getBoolean(properties, SearchableEntitySchema.Properties.CHANNEL_IS_ARCHIVED);
        if (isCourseWide != null) {
            metadata.put("isCourseWide", isCourseWide);
        }
        if (isPublic != null) {
            metadata.put("isPublic", isPublic);
        }
        if (isArchived != null) {
            metadata.put("isArchived", isArchived);
        }

        return new GlobalSearchResultDTO(idOrNull(properties), SearchableEntitySchema.TypeValues.CHANNEL, getString(properties, SearchableEntitySchema.Properties.TITLE),
                getString(properties, SearchableEntitySchema.Properties.DESCRIPTION), "Channel", metadata);
    }

    private static void addCourseContext(Map<String, Object> properties, Map<String, Object> metadata, Map<Long, String> courseNameById) {
        Long courseId = getLong(properties, SearchableEntitySchema.Properties.COURSE_ID);
        if (courseId != null) {
            metadata.put("courseId", courseId);
            String courseName = courseNameById.get(courseId);
            if (courseName != null) {
                metadata.put("courseName", courseName);
            }
        }
    }

    private static String idOrNull(Map<String, Object> properties) {
        Long entityId = getLong(properties, SearchableEntitySchema.Properties.ENTITY_ID);
        return entityId != null ? entityId.toString() : null;
    }

    private static String formatExerciseTypeBadge(String exerciseType) {
        if (exerciseType == null) {
            return "Exercise";
        }
        return switch (exerciseType.toLowerCase()) {
            case "programming" -> "Programming";
            case "modeling" -> "Modeling";
            case "quiz" -> "Quiz";
            case "text" -> "Text";
            case "file-upload", "fileupload" -> "File Upload";
            default -> "Exercise";
        };
    }

    private static void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static String getString(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        return value != null ? value.toString() : null;
    }

    private static Long getLong(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static Double getDouble(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static Integer getInteger(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static Boolean getBoolean(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return null;
    }
}
