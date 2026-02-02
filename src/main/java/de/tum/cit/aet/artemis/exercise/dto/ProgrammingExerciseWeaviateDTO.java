package de.tum.cit.aet.artemis.exercise.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateSchemas;

/**
 * DTO for programming exercise data retrieved from Weaviate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingExerciseWeaviateDTO(long exerciseId, long courseId, @Nullable String courseName, String title, @Nullable String shortName,
        @Nullable String problemStatement, @Nullable OffsetDateTime releaseDate, @Nullable OffsetDateTime startDate, @Nullable OffsetDateTime dueDate, String exerciseType,
        @Nullable String programmingLanguage, @Nullable String difficulty, double maxPoints, String baseUrl) {

    /**
     * Creates a ProgrammingExerciseWeaviateDTO from a Weaviate properties map.
     *
     * @param properties the properties map from Weaviate
     * @return the DTO
     */
    public static ProgrammingExerciseWeaviateDTO fromWeaviateProperties(Map<String, Object> properties) {
        return new ProgrammingExerciseWeaviateDTO(((Number) properties.get(WeaviateSchemas.ExercisesProperties.EXERCISE_ID)).longValue(),
                ((Number) properties.get(WeaviateSchemas.ExercisesProperties.COURSE_ID)).longValue(), (String) properties.get(WeaviateSchemas.ExercisesProperties.COURSE_NAME),
                (String) properties.get(WeaviateSchemas.ExercisesProperties.TITLE), (String) properties.get(WeaviateSchemas.ExercisesProperties.SHORT_NAME),
                (String) properties.get(WeaviateSchemas.ExercisesProperties.PROBLEM_STATEMENT), parseDate(properties.get(WeaviateSchemas.ExercisesProperties.RELEASE_DATE)),
                parseDate(properties.get(WeaviateSchemas.ExercisesProperties.START_DATE)), parseDate(properties.get(WeaviateSchemas.ExercisesProperties.DUE_DATE)),
                (String) properties.get(WeaviateSchemas.ExercisesProperties.EXERCISE_TYPE), (String) properties.get(WeaviateSchemas.ExercisesProperties.PROGRAMMING_LANGUAGE),
                (String) properties.get(WeaviateSchemas.ExercisesProperties.DIFFICULTY),
                properties.get(WeaviateSchemas.ExercisesProperties.MAX_POINTS) != null ? ((Number) properties.get(WeaviateSchemas.ExercisesProperties.MAX_POINTS)).doubleValue()
                        : 0.0,
                (String) properties.get(WeaviateSchemas.ExercisesProperties.BASE_URL));
    }

    @Nullable
    private static OffsetDateTime parseDate(@Nullable Object dateValue) {
        if (dateValue == null) {
            return null;
        }
        if (dateValue instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (dateValue instanceof String dateString) {
            return OffsetDateTime.parse(dateString);
        }
        return null;
    }
}
