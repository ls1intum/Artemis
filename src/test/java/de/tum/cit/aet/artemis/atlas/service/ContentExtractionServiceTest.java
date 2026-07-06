package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ContentExtractionServiceTest {

    private ContentExtractionService contentExtractionService;

    private QuizExerciseRepository quizExerciseRepository;

    @BeforeEach
    void setUp() {
        // Blank flavor-strip model + null ChatClient => stripFlavorText is a no-op passthrough for
        // non-blank text, so these extraction assertions are unaffected by the LLM strip path. The
        // quiz repository is only consulted for persisted quizzes (id != null); the in-memory quizzes
        // built here keep their questions, so it is never called.
        quizExerciseRepository = mock(QuizExerciseRepository.class);
        contentExtractionService = new ContentExtractionService(null, null, quizExerciseRepository, "", "low", 1.0);
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
    void extractContent_whitespaceProblemStatement_returnsEmptyLearningText() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("   ");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        // Whitespace-only text is treated as blank by stripFlavorText and collapses to empty.
        assertThat(result.extractedLearningText()).isEmpty();
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

    @Test
    void extractContent_textExercise_appendsExampleSolution() {
        TextExercise exercise = new TextExercise();
        exercise.setTitle("Essay");
        exercise.setProblemStatement("Write about recursion.");
        exercise.setExampleSolution("Recursion is when a function calls itself.");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.title()).isEqualTo("Essay");
        assertThat(result.extractedLearningText()).contains("Write about recursion.").contains("Example solution:").contains("Recursion is when a function calls itself.");
        assertThat(result.metadata()).containsEntry("exerciseType", "text");
    }

    @Test
    void extractContent_textExercise_withoutExampleSolution_returnsStatementOnly() {
        TextExercise exercise = new TextExercise();
        exercise.setTitle("Essay");
        exercise.setProblemStatement("Write about recursion.");
        exercise.setExampleSolution(null);

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.extractedLearningText()).isEqualTo("Write about recursion.");
        assertThat(result.extractedLearningText()).doesNotContain("Example solution:");
    }

    @Test
    void extractContent_modelingExercise_includesDiagramTypeAndSolutionExplanation() {
        ModelingExercise exercise = new ModelingExercise();
        exercise.setTitle("UML");
        exercise.setProblemStatement("Model a library.");
        exercise.setDiagramType(DiagramType.ClassDiagram);
        exercise.setExampleSolutionExplanation("The class diagram has Book and Member.");
        // The serialized Apollon model must not leak into the learning text (it is noise, not content).
        exercise.setExampleSolutionModel("{\"elements\":[]}");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.title()).isEqualTo("UML");
        assertThat(result.extractedLearningText()).contains("Model a library.").contains("The class diagram has Book and Member.").doesNotContain("elements");
        assertThat(result.metadata()).containsEntry("exerciseType", "modeling").containsEntry("diagramType", "classdiagram");
    }

    @Test
    void extractContent_fileUploadExercise_includesFilePattern() {
        FileUploadExercise exercise = new FileUploadExercise();
        exercise.setTitle("Upload report");
        exercise.setProblemStatement("Upload your report.");
        exercise.setExampleSolution("A good report has five sections.");
        exercise.setFilePattern("pdf,png");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.title()).isEqualTo("Upload report");
        assertThat(result.extractedLearningText()).contains("Upload your report.").contains("A good report has five sections.");
        assertThat(result.metadata()).containsEntry("exerciseType", "file-upload").containsEntry("filePattern", "pdf,png");
    }

    @Test
    void extractContent_quizExercise_assemblesQuestionsAnswersAndSolutions() {
        QuizExercise quiz = new QuizExercise();
        quiz.setTitle("Data structures quiz");
        quiz.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion());
        quiz.addQuestion(QuizExerciseFactory.createDragAndDropQuestion());
        quiz.addQuestion(QuizExerciseFactory.createShortAnswerQuestion());

        ExtractedContentDTO result = contentExtractionService.extractContent(quiz);

        assertThat(result.title()).isEqualTo("Data structures quiz");
        String text = result.extractedLearningText();
        // Multiple choice: prompt + each option with its correctness marker and explanation.
        assertThat(text).contains("Q1").contains("A [correct]").contains("B [incorrect]").contains("E1");
        // Drag and drop: prompt + the text drag items (picture-only items carry no text and are skipped).
        assertThat(text).contains("Q2").contains("D1").contains("D3").doesNotContain("dragItemImage");
        // Short answer: prompt + the correct spot -> solution mappings.
        assertThat(text).contains("This is a long answer text").contains("Spot 0: is").contains("Spot 2: long");
        assertThat(result.metadata()).containsEntry("exerciseType", "quiz").containsEntry("questionCount", "3");
        // In-memory quiz (no id): the repository re-fetch path is not taken.
        verifyNoInteractions(quizExerciseRepository);
    }

    @Test
    void extractContent_quizExercise_withoutQuestions_returnsEmptyTextAndZeroCount() {
        QuizExercise quiz = new QuizExercise();
        quiz.setTitle("Empty quiz");

        ExtractedContentDTO result = contentExtractionService.extractContent(quiz);

        assertThat(result.title()).isEqualTo("Empty quiz");
        assertThat(result.extractedLearningText()).isEmpty();
        assertThat(result.metadata()).containsEntry("questionCount", "0");
    }

    @Test
    void extractContent_quizShortAnswer_withoutMappings_rendersAcceptedAnswers() {
        ShortAnswerQuestion question = new ShortAnswerQuestion();
        question.setTitle("Fill in");
        ShortAnswerSolution solution = new ShortAnswerSolution();
        solution.setText("answer alpha");
        question.setSolutions(List.of(solution));
        // No correct mappings: rendering must fall back to the accepted-answers (solutions) list.

        QuizExercise quiz = new QuizExercise();
        quiz.setTitle("Short answer quiz");
        quiz.addQuestion(question);

        ExtractedContentDTO result = contentExtractionService.extractContent(quiz);

        assertThat(result.extractedLearningText()).contains("Accepted answers:").contains("- answer alpha").doesNotContain("Correct answers by spot:");
    }

    @Test
    void extractContent_quizShortAnswer_mappingsWithoutSolution_fallsBackToAcceptedAnswers() {
        ShortAnswerQuestion question = new ShortAnswerQuestion();
        question.setTitle("Fill in");
        ShortAnswerSpot spot = new ShortAnswerSpot();
        spot.setSpotNr(1);
        ShortAnswerMapping mapping = new ShortAnswerMapping();
        mapping.setSpot(spot);
        // Solution intentionally left null so the mapping is filtered out and no spot header should be emitted.
        question.setCorrectMappings(Set.of(mapping));
        ShortAnswerSolution solution = new ShortAnswerSolution();
        solution.setText("answer beta");
        question.setSolutions(List.of(solution));

        QuizExercise quiz = new QuizExercise();
        quiz.setTitle("Short answer quiz");
        quiz.addQuestion(question);

        ExtractedContentDTO result = contentExtractionService.extractContent(quiz);

        // Every mapping is filtered out, so the header is suppressed and rendering falls through to the solutions list.
        assertThat(result.extractedLearningText()).doesNotContain("Correct answers by spot:").contains("Accepted answers:").contains("- answer beta");
    }

    @Test
    void extractContent_textExercise_blankStatementWithSolution_returnsLabeledSolutionOnly() {
        TextExercise exercise = new TextExercise();
        exercise.setTitle("Essay");
        exercise.setProblemStatement("   ");
        exercise.setExampleSolution("Recursion is when a function calls itself.");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        // Blank statement collapses away; the output is exactly the labeled solution with no leading separators.
        assertThat(result.extractedLearningText()).isEqualTo("Example solution:\nRecursion is when a function calls itself.");
    }

    @Test
    void extractContent_modelingExercise_nullDiagramType_omitsFromMetadata() {
        ModelingExercise exercise = new ModelingExercise();
        exercise.setTitle("UML");
        exercise.setProblemStatement("Model a library.");
        exercise.setDiagramType(null);

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.metadata()).containsEntry("exerciseType", "modeling").doesNotContainKey("diagramType");
    }

    @Test
    void extractContent_fileUploadExercise_blankFilePattern_omitsFromMetadata() {
        FileUploadExercise exercise = new FileUploadExercise();
        exercise.setTitle("Upload report");
        exercise.setProblemStatement("Upload your report.");
        exercise.setFilePattern("  ");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.metadata()).containsEntry("exerciseType", "file-upload").doesNotContainKey("filePattern");
    }

}
