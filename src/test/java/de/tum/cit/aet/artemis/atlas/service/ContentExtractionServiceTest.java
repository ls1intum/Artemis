package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class ContentExtractionServiceTest {

    private ContentExtractionService contentExtractionService;

    @BeforeEach
    void setUp() {
        contentExtractionService = new ContentExtractionService();
    }

    @Test
    void extractContent_programmingExercise_populatesTitleAndLearningText() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("Implement a sorting algorithm that handles edge cases.");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.title()).isEqualTo("Sorting");
        assertThat(result.extractedLearningText()).isEqualTo("Implement a sorting algorithm that handles edge cases.");
        assertThat(result.metadata()).containsEntry("exerciseType", "programming");
    }

    @Test
    void extractContent_nullProblemStatement_returnsEmptyLearningText() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement(null);

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.title()).isEqualTo("Sorting");
        assertThat(result.extractedLearningText()).isEmpty();
    }

    @Test
    void extractContent_withDifficultyAndMaxPoints_includesInMetadata() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("Sort things.");
        exercise.setDifficulty(DifficultyLevel.HARD);
        exercise.setMaxPoints(100.0);

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.metadata()).containsEntry("difficulty", "hard");
        assertThat(result.metadata()).containsEntry("maxPoints", "100.0");
    }

    @Test
    void extractContent_nullDifficultyAndMaxPoints_omitsFromMetadata() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("Sort things.");
        exercise.setDifficulty(null);
        exercise.setMaxPoints(null);

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.metadata()).containsKey("exerciseType");
        assertThat(result.metadata()).doesNotContainKey("difficulty");
        assertThat(result.metadata()).doesNotContainKey("maxPoints");
    }

    @Test
    void extractContent_unsupportedExerciseType_throwsIllegalArgumentException() {
        ModelingExercise exercise = new ModelingExercise();
        exercise.setTitle("UML Diagram");

        assertThatThrownBy(() -> contentExtractionService.extractContent(exercise)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported exercise type");
    }

    @Test
    void toJson_producesValidJson() throws JsonProcessingException {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("Implement sorting.");
        exercise.setDifficulty(DifficultyLevel.MEDIUM);
        exercise.setMaxPoints(50.0);

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);
        String json = result.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);

        assertThat(node.get("title").asText()).isEqualTo("Sorting");
        assertThat(node.get("extractedLearningText").asText()).isEqualTo("Implement sorting.");
        assertThat(node.get("metadata").get("exerciseType").asText()).isEqualTo("programming");
        assertThat(node.get("metadata").get("difficulty").asText()).isEqualTo("medium");
        assertThat(node.get("metadata").get("maxPoints").asText()).isEqualTo("50.0");
    }
}
