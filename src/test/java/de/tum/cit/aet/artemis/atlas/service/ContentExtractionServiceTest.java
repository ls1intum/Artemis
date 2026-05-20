package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
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
    void extractContent_nullTitle_returnsEmptyTitle() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle(null);
        exercise.setProblemStatement("Some statement.");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.title()).isEmpty();
    }

    @Test
    void extractContent_emptyProblemStatement_returnsEmptyLearningText() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.extractedLearningText()).isEmpty();
    }

    @Test
    void extractContent_nullLearningObject_throwsException() {
        assertThatThrownBy(() -> contentExtractionService.extractContent(null)).isInstanceOf(NullPointerException.class).hasMessage("learningObject must not be null");
    }

    @Test
    void extractContent_unsupportedLearningObjectType_throwsIllegalArgumentException() {
        TextUnit textUnit = new TextUnit();
        textUnit.setName("Intro");

        assertThatThrownBy(() -> contentExtractionService.extractContent(textUnit)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported learning object type");
    }

    @Test
    void extractContent_metadataIsImmutable_throwsOnMutation() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("Sort things.");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThatThrownBy(() -> result.metadata().put("foo", "bar")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void extractContent_whitespaceProblemStatement_passesThrough() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("   ");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.extractedLearningText()).isEqualTo("   ");
    }

    @Test
    void extractContent_maxPointsWithFloatingPointDrift_isFormatted() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("Sort things.");
        exercise.setMaxPoints(0.1 + 0.2);

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.metadata()).containsEntry("maxPoints", "0.3");
    }

}
