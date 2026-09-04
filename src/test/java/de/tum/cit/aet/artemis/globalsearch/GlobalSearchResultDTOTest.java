package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.GlobalSearchResultDTO;

/**
 * Unit tests for {@link GlobalSearchResultDTO} badge derivation.
 * <p>
 * The badge is a stable machine key the client resolves to a localised label via {@code global.search.results.badge.*}.
 * These tests pin the exact keys the server emits so a rename here can never silently desync from the client i18n keys
 * (which are asserted to be present and symmetric in the client spec).
 */
class GlobalSearchResultDTOTest {

    private static GlobalSearchResultDTO fromProperties(Map<String, Object> properties) {
        return GlobalSearchResultDTO.fromSearchableItemProperties(properties, Map.of(), Map.of(), Set.of(), Set.of(), Map.of());
    }

    private static Map<String, Object> row(String type) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, type);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, 1L);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, 1L);
        properties.put(SearchableEntitySchema.Properties.TITLE, "Title");
        return properties;
    }

    private static Map<String, Object> exerciseRow(String exerciseType) {
        Map<String, Object> properties = row(SearchableEntitySchema.TypeValues.EXERCISE);
        if (exerciseType != null) {
            properties.put(SearchableEntitySchema.Properties.EXERCISE_TYPE, exerciseType);
        }
        return properties;
    }

    @Test
    void exerciseBadgeIsTheRawExerciseTypeKey() {
        assertThat(fromProperties(exerciseRow(ExerciseType.PROGRAMMING.getValue())).badge()).isEqualTo("programming");
        assertThat(fromProperties(exerciseRow(ExerciseType.MODELING.getValue())).badge()).isEqualTo("modeling");
        assertThat(fromProperties(exerciseRow(ExerciseType.QUIZ.getValue())).badge()).isEqualTo("quiz");
        assertThat(fromProperties(exerciseRow(ExerciseType.TEXT.getValue())).badge()).isEqualTo("text");
        assertThat(fromProperties(exerciseRow(ExerciseType.FILE_UPLOAD.getValue())).badge()).isEqualTo("file-upload");
    }

    @Test
    void exerciseBadgeFallsBackToGenericExerciseWhenTypeIsMissing() {
        assertThat(fromProperties(exerciseRow(null)).badge()).isEqualTo("exercise");
    }

    @Test
    void examBadgeDistinguishesTestExamFromExam() {
        Map<String, Object> exam = row(SearchableEntitySchema.TypeValues.EXAM);
        assertThat(fromProperties(exam).badge()).isEqualTo("exam");

        Map<String, Object> testExam = row(SearchableEntitySchema.TypeValues.EXAM);
        testExam.put(SearchableEntitySchema.Properties.TEST_EXAM, true);
        assertThat(fromProperties(testExam).badge()).isEqualTo("test-exam");
    }

    @Test
    void nonExerciseBadgesAreStableTypeKeys() {
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.LECTURE)).badge()).isEqualTo("lecture");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.LECTURE_UNIT)).badge()).isEqualTo("lecture-unit");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.FAQ)).badge()).isEqualTo("faq");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.CHANNEL)).badge()).isEqualTo("channel");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.COURSE)).badge()).isEqualTo("course");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.POST)).badge()).isEqualTo("message");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.ANSWER_POST)).badge()).isEqualTo("message");
    }

    /**
     * Every badge key the server can emit must have a matching client i18n label in both languages, otherwise the
     * results list would render the raw key. This guards the server-to-client contract that a pure server or pure
     * client test cannot see on its own.
     */
    @Test
    void everyEmittableBadgeKeyHasAnEnglishAndGermanLabel() throws IOException {
        // Exercise keys are derived from the enum so a newly added ExerciseType without a matching i18n label fails here.
        List<String> emittableKeys = new ArrayList<>(Arrays.stream(ExerciseType.values()).map(ExerciseType::getValue).toList());
        emittableKeys.addAll(List.of("exercise", "exam", "test-exam", "lecture", "lecture-unit", "faq", "channel", "course", "message"));

        assertThat(badgeLabels("en")).containsKeys(emittableKeys.toArray(new String[0]));
        assertThat(badgeLabels("de")).containsKeys(emittableKeys.toArray(new String[0]));
    }

    private static Map<String, String> badgeLabels(String language) throws IOException {
        JsonNode badge = new ObjectMapper().readTree(Files.readString(Path.of("src/main/webapp/i18n", language, "global.json"))).path("global").path("search").path("results")
                .path("badge");
        Map<String, String> labels = new HashMap<>();
        badge.properties().forEach(entry -> labels.put(entry.getKey(), entry.getValue().asText()));
        return labels;
    }
}
