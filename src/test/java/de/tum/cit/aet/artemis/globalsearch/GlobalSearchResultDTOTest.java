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
 * The badge key is a stable machine key the web client resolves to a localised label via {@code global.search.results.badge.*},
 * while the badge itself stays the English label other clients display as is. These tests pin both, so a rename here can
 * never silently desync from the client i18n keys or change what those other clients show.
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
    void exerciseBadgeKeyIsTheRawExerciseType() {
        assertThat(fromProperties(exerciseRow(ExerciseType.PROGRAMMING.getValue())).badgeKey()).isEqualTo("programming");
        assertThat(fromProperties(exerciseRow(ExerciseType.MODELING.getValue())).badgeKey()).isEqualTo("modeling");
        assertThat(fromProperties(exerciseRow(ExerciseType.QUIZ.getValue())).badgeKey()).isEqualTo("quiz");
        assertThat(fromProperties(exerciseRow(ExerciseType.TEXT.getValue())).badgeKey()).isEqualTo("text");
        assertThat(fromProperties(exerciseRow(ExerciseType.FILE_UPLOAD.getValue())).badgeKey()).isEqualTo("file-upload");
    }

    /**
     * The badge stays the English display label it has always been, because clients other than the web client, such as the iOS app, render it as is.
     */
    @Test
    void exerciseBadgeIsTheEnglishLabel() {
        assertThat(fromProperties(exerciseRow(ExerciseType.PROGRAMMING.getValue())).badge()).isEqualTo("Programming");
        assertThat(fromProperties(exerciseRow(ExerciseType.MODELING.getValue())).badge()).isEqualTo("Modeling");
        assertThat(fromProperties(exerciseRow(ExerciseType.QUIZ.getValue())).badge()).isEqualTo("Quiz");
        assertThat(fromProperties(exerciseRow(ExerciseType.TEXT.getValue())).badge()).isEqualTo("Text");
        assertThat(fromProperties(exerciseRow(ExerciseType.FILE_UPLOAD.getValue())).badge()).isEqualTo("File Upload");
    }

    /**
     * A new exercise type without a badge label would silently fall back to the generic badge, so every type has to be known.
     */
    @Test
    void everyExerciseTypeHasItsOwnBadge() {
        for (ExerciseType exerciseType : ExerciseType.values()) {
            GlobalSearchResultDTO result = fromProperties(exerciseRow(exerciseType.getValue()));
            assertThat(result.badgeKey()).as("badge key for %s", exerciseType).isEqualTo(exerciseType.getValue());
            assertThat(result.badge()).as("badge label for %s", exerciseType).isNotEqualTo("Exercise");
        }
    }

    @Test
    void exerciseBadgeFallsBackToGenericExerciseWhenTypeIsMissing() {
        GlobalSearchResultDTO result = fromProperties(exerciseRow(null));
        assertThat(result.badgeKey()).isEqualTo("exercise");
        assertThat(result.badge()).isEqualTo("Exercise");
    }

    @Test
    void exerciseBadgeFallsBackToGenericExerciseWhenTypeIsUnknown() {
        // The client builds the translation key by concatenation, so a value it has no catalogue entry for would
        // render the unresolved key in the result list. Indexing only writes ExerciseType values today, but the
        // store is external and the enum can grow, so the fallback has to cover more than null.
        GlobalSearchResultDTO result = fromProperties(exerciseRow("survey"));
        assertThat(result.badgeKey()).isEqualTo("exercise");
        assertThat(result.badge()).isEqualTo("Exercise");
    }

    @Test
    void examBadgeDistinguishesTestExamFromExam() {
        GlobalSearchResultDTO exam = fromProperties(row(SearchableEntitySchema.TypeValues.EXAM));
        assertThat(exam.badgeKey()).isEqualTo("exam");
        assertThat(exam.badge()).isEqualTo("Exam");

        Map<String, Object> testExamRow = row(SearchableEntitySchema.TypeValues.EXAM);
        testExamRow.put(SearchableEntitySchema.Properties.TEST_EXAM, true);
        GlobalSearchResultDTO testExam = fromProperties(testExamRow);
        assertThat(testExam.badgeKey()).isEqualTo("test-exam");
        assertThat(testExam.badge()).isEqualTo("Test Exam");
    }

    @Test
    void nonExerciseBadgeKeysAreStableTypeKeys() {
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.LECTURE)).badgeKey()).isEqualTo("lecture");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.LECTURE_UNIT)).badgeKey()).isEqualTo("lecture-unit");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.FAQ)).badgeKey()).isEqualTo("faq");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.CHANNEL)).badgeKey()).isEqualTo("channel");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.COURSE)).badgeKey()).isEqualTo("course");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.POST)).badgeKey()).isEqualTo("message");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.ANSWER_POST)).badgeKey()).isEqualTo("message");
    }

    @Test
    void nonExerciseBadgesAreEnglishLabels() {
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.LECTURE)).badge()).isEqualTo("Lecture");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.LECTURE_UNIT)).badge()).isEqualTo("Lecture Unit");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.FAQ)).badge()).isEqualTo("FAQ");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.CHANNEL)).badge()).isEqualTo("Channel");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.COURSE)).badge()).isEqualTo("Course");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.POST)).badge()).isEqualTo("Message");
        assertThat(fromProperties(row(SearchableEntitySchema.TypeValues.ANSWER_POST)).badge()).isEqualTo("Message");
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
