package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified DTO for global search results across all entity types.
 * This DTO provides a consistent structure for search results regardless of the entity type
 * (exercises, pages, features, courses, etc.), making it easy to render in a unified search UI.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GlobalSearchResultDTO(String id, String type, String title, String description, String badge, Map<String, Object> metadata) {

    /**
     * Creates a search result DTO from exercise Weaviate properties.
     *
     * @param properties the property map from Weaviate search results
     * @return the unified search result DTO
     */
    public static GlobalSearchResultDTO fromExerciseProperties(Map<String, Object> properties) {
        String exerciseType = getString(properties, "exercise_type");
        String badge = formatExerciseTypeBadge(exerciseType);
        String title = getString(properties, "title");
        String problemStatement = getString(properties, "problem_statement");

        // Build metadata map with relevant exercise information
        Map<String, Object> metadata = new HashMap<>();

        // Add course ID (required for navigation)
        Long courseId = getLong(properties, "course_id");
        if (courseId != null) {
            metadata.put("courseId", courseId);
        }

        // Add due date if present (raw ISO string — client handles locale-aware formatting)
        String dueDate = getString(properties, "due_date");
        if (dueDate != null) {
            metadata.put("dueDate", dueDate);
        }

        // Add release date if present (raw ISO string — client handles locale-aware formatting)
        String releaseDate = getString(properties, "release_date");
        if (releaseDate != null) {
            metadata.put("releaseDate", releaseDate);
        }

        // Add points (preserve fractional values like 0.5, 1.5)
        Double maxPoints = getDouble(properties, "max_points");
        if (maxPoints != null) {
            metadata.put("points", maxPoints);
        }

        // Add course information
        String courseName = getString(properties, "course_name");
        if (courseName != null) {
            metadata.put("courseName", courseName);
        }

        // Add difficulty if present
        String difficulty = getString(properties, "difficulty");
        if (difficulty != null) {
            metadata.put("difficulty", difficulty);
        }

        // Add type-specific metadata
        addTypeSpecificMetadata(properties, exerciseType, metadata);

        Long exerciseId = getLong(properties, "exercise_id");
        String id = exerciseId != null ? exerciseId.toString() : null;

        return new GlobalSearchResultDTO(id, "exercise", title, problemStatement, badge, metadata);
    }

    /**
     * Adds type-specific metadata based on exercise type.
     *
     * @param properties   the Weaviate properties
     * @param exerciseType the exercise type
     * @param metadata     the metadata map to populate
     */
    private static void addTypeSpecificMetadata(Map<String, Object> properties, String exerciseType, Map<String, Object> metadata) {
        if ("programming".equals(exerciseType)) {
            String programmingLanguage = getString(properties, "programming_language");
            if (programmingLanguage != null) {
                metadata.put("programmingLanguage", programmingLanguage);
            }
        }
        else if ("modeling".equals(exerciseType)) {
            String diagramType = getString(properties, "diagram_type");
            if (diagramType != null) {
                metadata.put("diagramType", diagramType);
            }
        }
        else if ("quiz".equals(exerciseType)) {
            Integer quizDuration = getInteger(properties, "quiz_duration");
            if (quizDuration != null) {
                metadata.put("quizDuration", quizDuration);
            }
        }
    }

    /**
     * Formats exercise type as a user-friendly badge label.
     *
     * @param exerciseType the exercise type from Weaviate
     * @return formatted badge text
     */
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

    private static String getString(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        return value != null ? value.toString() : null;
    }

    private static Long getLong(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static Double getDouble(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static Integer getInteger(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
