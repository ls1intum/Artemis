package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exercise search results from Weaviate.
 * Contains the essential exercise metadata returned in search queries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExerciseSearchResultDTO(Long exerciseId, Long courseId, String courseTitle, String title, String shortName, String problemStatement, String exerciseType,
        Double maxPoints, String difficulty, Boolean isExamExercise, String programmingLanguage, String projectType, String diagramType, String quizMode, Integer quizDuration,
        String filePattern) {

    /**
     * Creates a search result DTO from Weaviate property map.
     *
     * @param properties the property map from Weaviate search results
     * @return the search result DTO
     */
    public static ExerciseSearchResultDTO fromWeaviateProperties(Map<String, Object> properties) {
        return new ExerciseSearchResultDTO(getLong(properties, "exercise_id"), getLong(properties, "course_id"), getString(properties, "course_name"),
                getString(properties, "title"), getString(properties, "short_name"), getString(properties, "problem_statement"), getString(properties, "exercise_type"),
                getDouble(properties, "max_points"), getString(properties, "difficulty"), getBoolean(properties, "is_exam_exercise"), getString(properties, "programming_language"),
                getString(properties, "project_type"), getString(properties, "diagram_type"), getString(properties, "quiz_mode"), getInteger(properties, "quiz_duration"),
                getString(properties, "file_pattern"));
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

    private static Boolean getBoolean(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return null;
    }
}
