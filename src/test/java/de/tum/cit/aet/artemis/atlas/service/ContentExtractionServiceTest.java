package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ContentExtractionServiceTest {

    private ContentExtractionService contentExtractionService;

    private QuizExerciseTestRepository quizExerciseRepository;

    @BeforeEach
    void setUp() {
        // Blank flavor-strip model + null ChatClient => stripFlavorText is a no-op passthrough for
        // non-blank text, so these extraction assertions are unaffected by the LLM strip path. The
        // quiz repository is only consulted for persisted quizzes (id != null); the in-memory quizzes
        // built here keep their questions, so it is never called.
        quizExerciseRepository = mock(QuizExerciseTestRepository.class);
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
        // A LearningObject that is neither an exercise nor a lecture-unit subtype hits the defensive default branch.
        LearningObject unsupported = mock(LearningObject.class);

        assertThatThrownBy(() -> contentExtractionService.extractContent(unsupported)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported learning object type");
    }

    @Test
    void extractContent_exerciseUnit_throwsBecauseNeverOrchestrated() {
        ExerciseUnit exerciseUnit = new ExerciseUnit();

        assertThatThrownBy(() -> contentExtractionService.extractContent(exerciseUnit)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ExerciseUnit is never orchestrated");
    }

    @Test
    void extractContent_textUnit_populatesTitleAndContent() {
        TextUnit unit = new TextUnit();
        unit.setName("Recursion basics");
        unit.setContent("A recursive function calls itself until a base case is reached.");

        ExtractedContentDTO result = contentExtractionService.extractContent(unit);

        assertThat(result.title()).isEqualTo("Recursion basics");
        assertThat(result.extractedLearningText()).isEqualTo("A recursive function calls itself until a base case is reached.");
        assertThat(result.metadata()).containsEntry("lectureUnitType", "text");
    }

    @Test
    void extractContent_textUnit_blankContent_returnsEmptyLearningText() {
        TextUnit unit = new TextUnit();
        unit.setName("Empty");
        unit.setContent("   ");

        ExtractedContentDTO result = contentExtractionService.extractContent(unit);

        assertThat(result.extractedLearningText()).isEmpty();
        assertThat(result.metadata()).containsEntry("lectureUnitType", "text");
    }

    @Test
    void extractContent_onlineUnit_usesDescriptionAndRecordsSource() {
        OnlineUnit unit = new OnlineUnit();
        unit.setName("Spring docs");
        unit.setDescription("Read the dependency-injection chapter.");
        unit.setSource("https://spring.io/guides");

        ExtractedContentDTO result = contentExtractionService.extractContent(unit);

        assertThat(result.title()).isEqualTo("Spring docs");
        assertThat(result.extractedLearningText()).isEqualTo("Read the dependency-injection chapter.");
        assertThat(result.metadata()).containsEntry("lectureUnitType", "online").containsEntry("source", "https://spring.io/guides");
    }

    @Test
    void extractContent_onlineUnit_blankSource_omitsSourceMetadata() {
        OnlineUnit unit = new OnlineUnit();
        unit.setName("Notes");
        unit.setDescription("Some notes.");
        unit.setSource("  ");

        ExtractedContentDTO result = contentExtractionService.extractContent(unit);

        assertThat(result.metadata()).containsEntry("lectureUnitType", "online").doesNotContainKey("source");
    }

    @Test
    void extractContent_attachmentVideoUnit_usesDescription() {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setName("Lecture 3 slides");
        unit.setDescription("Covers hashing and collision resolution.");
        unit.setVideoSource(" https://videos.example/lecture-3 ");
        Attachment attachment = new Attachment();
        attachment.setLink(" /api/core/files/lecture-3.pdf ");
        unit.setAttachment(attachment);

        ExtractedContentDTO result = contentExtractionService.extractContent(unit);

        assertThat(result.title()).isEqualTo("Lecture 3 slides");
        assertThat(result.extractedLearningText()).isEqualTo("Covers hashing and collision resolution.");
        assertThat(result.metadata()).containsEntry("lectureUnitType", "attachment").containsEntry("videoSource", "https://videos.example/lecture-3")
                .containsEntry("attachmentLink", "/api/core/files/lecture-3.pdf");
    }

    @Test
    void extractContent_attachmentVideoUnit_blankDescription_returnsEmptyLearningText() {
        // A file/video-only unit carries no description; the orchestrator treats the empty learning text as "skip".
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setName("Lecture recording");
        unit.setDescription(null);

        ExtractedContentDTO result = contentExtractionService.extractContent(unit);

        assertThat(result.title()).isEqualTo("Lecture recording");
        assertThat(result.extractedLearningText()).isEmpty();
        assertThat(result.metadata()).containsEntry("lectureUnitType", "attachment");
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
        // Drag and drop: prompt + the text drag items (picture-only items carry no text and are skipped) + the
        // correct drag-item-to-drop-zone solution, referencing each geometry-only drop location by its 1-based position.
        assertThat(text).contains("Q2").contains("D1").contains("D3").doesNotContain("dragItemImage");
        assertThat(text).contains("Correct drop mapping:").contains("D1 -> drop zone 1").contains("D3 -> drop zone 3");
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void extractContent_textExercise_absentStatementWithSolution_returnsLabeledSolutionOnly(String problemStatement) {
        TextExercise exercise = new TextExercise();
        exercise.setTitle("Essay");
        exercise.setProblemStatement(problemStatement);
        exercise.setExampleSolution("Recursion is when a function calls itself.");

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        // A null, empty or blank statement collapses away; the output is exactly the labeled solution with no leading separators.
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    void extractContent_fileUploadExercise_absentFilePattern_omitsFromMetadata(String filePattern) {
        FileUploadExercise exercise = new FileUploadExercise();
        exercise.setTitle("Upload report");
        exercise.setProblemStatement("Upload your report.");
        exercise.setFilePattern(filePattern);

        ExtractedContentDTO result = contentExtractionService.extractContent(exercise);

        assertThat(result.metadata()).containsEntry("exerciseType", "file-upload").doesNotContainKey("filePattern");
    }

}
