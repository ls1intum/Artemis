package de.tum.cit.aet.artemis.exercise.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.ExerciseSchema;

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
    public static ProgrammingExerciseWeaviateDTO fromWeaviateProperties(Map<String, Object> properties, String baseUrl) {
        return new ProgrammingExerciseWeaviateDTO(((Number) properties.get(ExerciseSchema.Properties.EXERCISE_ID)).longValue(),
                ((Number) properties.get(ExerciseSchema.Properties.COURSE_ID)).longValue(), (String) properties.get(ExerciseSchema.Properties.COURSE_NAME),
                (String) properties.get(ExerciseSchema.Properties.TITLE), (String) properties.get(ExerciseSchema.Properties.SHORT_NAME),
                (String) properties.get(ExerciseSchema.Properties.PROBLEM_STATEMENT), parseDate(properties.get(ExerciseSchema.Properties.RELEASE_DATE)),
                parseDate(properties.get(ExerciseSchema.Properties.START_DATE)), parseDate(properties.get(ExerciseSchema.Properties.DUE_DATE)),
                (String) properties.get(ExerciseSchema.Properties.EXERCISE_TYPE), (String) properties.get(ExerciseSchema.Properties.PROGRAMMING_LANGUAGE),
                (String) properties.get(ExerciseSchema.Properties.DIFFICULTY),
                properties.get(ExerciseSchema.Properties.MAX_POINTS) != null ? ((Number) properties.get(ExerciseSchema.Properties.MAX_POINTS)).doubleValue() : 0.0, baseUrl);
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
