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
        // The parameter is declared @NonNull rather than asserted with Objects.requireNonNull, so the message is the
        // one the JVM produces for the pattern-matching switch that dereferences it, not one this service chose.
        assertThatThrownBy(() -> contentExtractionService.extractContent(null)).isInstanceOf(NullPointerException.class);
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
    void isLectureUnitEligibleForOrchestration_textUnitWithContent_isEligible() {
        TextUnit unit = new TextUnit();
        unit.setContent("A recursive function calls itself until a base case is reached.");

        assertThat(ContentExtractionService.isLectureUnitEligibleForOrchestration(unit)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t\n" })
    void isLectureUnitEligibleForOrchestration_blankTextUnit_isIneligible(String content) {
        // Blank text extracts to an empty learning text that batch orchestration drops, so it must never be queued or offered.
        TextUnit unit = new TextUnit();
        unit.setContent(content);

        assertThat(ContentExtractionService.isLectureUnitEligibleForOrchestration(unit)).isFalse();
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
                .containsEntry("attachmentLink", "lecture-3.pdf");
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

    @Test
    void quizRenderingSkipsIncompleteOptionsAndMappingsWithoutLosingQuestionStructure() {
        var quiz = new QuizExercise();
        var mc = new de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion();
        mc.setTitle(null);
        mc.setText(" ");
        mc.setHint(" Hint ");
        mc.setExplanation(" Explanation ");
        var answer = new de.tum.cit.aet.artemis.quiz.domain.AnswerOption();
        answer.setText(null);
        answer.setIsCorrect(false);
        answer.setExplanation(" ");
        mc.setAnswerOptions(List.of(answer));
        var shortAnswer = new ShortAnswerQuestion();
        shortAnswer.setTitle(" ");
        var invalidSpot = new ShortAnswerMapping();
        invalidSpot.setSolution(new ShortAnswerSolution());
        var invalidSolution = new ShortAnswerMapping();
        invalidSolution.setSpot(new ShortAnswerSpot());
        shortAnswer.setCorrectMappings(Set.of(invalidSpot, invalidSolution));
        var nullSolution = new ShortAnswerSolution();
        nullSolution.setText(null);
        var blankSolution = new ShortAnswerSolution();
        blankSolution.setText(" ");
        shortAnswer.setSolutions(List.of(nullSolution, blankSolution));
        var emptyMc = new de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion();
        emptyMc.setAnswerOptions(null);
        var emptyShortAnswer = new ShortAnswerQuestion();
        emptyShortAnswer.setCorrectMappings(null);
        emptyShortAnswer.setSolutions(null);
        quiz.setQuizQuestions(List.of(mc, shortAnswer, emptyMc, emptyShortAnswer));
        var text = contentExtractionService.extractContent(quiz).extractedLearningText();
        assertThat(text)
                .isEqualTo("Question 1\nHint: Hint\nExplanation: Explanation\nAnswer options:\n-  [incorrect]\n\n\nQuestion 2\nAccepted answers:\n\n\nQuestion 3\n\n\nQuestion 4");
    }

    @Test
    void dragAndDropRenderingFiltersBrokenMappingsAndSortsValidZones() {
        var quiz = new QuizExercise();
        var question = new de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion();
        var item = new de.tum.cit.aet.artemis.quiz.domain.DragItem();
        item.setText(" value ");
        var blank = new de.tum.cit.aet.artemis.quiz.domain.DragItem();
        blank.setText(" ");
        var nullText = new de.tum.cit.aet.artemis.quiz.domain.DragItem();
        var zone = new de.tum.cit.aet.artemis.quiz.domain.DropLocation();
        zone.setId(1L);
        var outside = new de.tum.cit.aet.artemis.quiz.domain.DropLocation();
        outside.setId(2L);
        question.setDragItems(List.of(blank, nullText, item));
        question.setDropLocations(List.of(zone));
        var valid = new de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping();
        valid.setDragItem(item);
        valid.setDropLocation(zone);
        var missingItem = new de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping();
        missingItem.setDropLocation(zone);
        var missingZone = new de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping();
        missingZone.setDragItem(item);
        var outsideZone = new de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping();
        outsideZone.setDragItem(item);
        outsideZone.setDropLocation(outside);
        var emptyItem = new de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping();
        emptyItem.setDragItem(blank);
        emptyItem.setDropLocation(zone);
        var nullItemText = new de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping();
        nullItemText.setDragItem(nullText);
        nullItemText.setDropLocation(zone);
        question.setCorrectMappings(Set.of(valid, missingItem, missingZone, outsideZone, emptyItem, nullItemText));
        quiz.setQuizQuestions(List.of(question));
        assertThat(contentExtractionService.extractContent(quiz).extractedLearningText())
                .isEqualTo("Question 1\nDrag items:\n- value\nCorrect drop mapping:\n- value -> drop zone 1");
        question.setCorrectMappings(Set.of(outsideZone));
        assertThat(contentExtractionService.extractContent(quiz).extractedLearningText()).isEqualTo("Question 1\nDrag items:\n- value");
        question.setDropLocations(null);
        assertThat(contentExtractionService.extractContent(quiz).extractedLearningText()).isEqualTo("Question 1\nDrag items:\n- value");
        question.setDragItems(null);
        assertThat(contentExtractionService.extractContent(quiz).extractedLearningText()).isEqualTo("Question 1");
    }

    @Test
    void shortAnswerMappingWithMissingSpotNumberAndTextStillRendersDeterministically() {
        var quiz = new QuizExercise();
        var question = new ShortAnswerQuestion();
        var mapping = new ShortAnswerMapping();
        var spot = new ShortAnswerSpot();
        var solution = new ShortAnswerSolution();
        question.setSpots(List.of(spot));
        question.setSolutions(List.of(solution));
        mapping.setSpot(spot);
        mapping.setSolution(solution);
        question.setCorrectMappings(Set.of(mapping));
        quiz.setQuizQuestions(List.of(question));
        assertThat(contentExtractionService.extractContent(quiz).extractedLearningText()).isEqualTo("Question 1\nCorrect answers by spot:\n- Spot 0:");
    }

    @Test
    void attachmentMetadataOmitsMissingOrBlankLinksAndSources() {
        var unit = new AttachmentVideoUnit();
        unit.setVideoSource(" ");
        var attachment = new Attachment();
        unit.setAttachment(attachment);
        assertThat(contentExtractionService.extractContent(unit).metadata()).doesNotContainKeys("videoSource", "attachmentLink");
        attachment.setLink(" ");
        assertThat(contentExtractionService.extractContent(unit).metadata()).doesNotContainKey("attachmentLink");
        var online = new OnlineUnit();
        online.setSource(" ");
        assertThat(contentExtractionService.extractContent(online).metadata()).doesNotContainKey("source");
    }

    @Test
    void courseLectureEligibilityRequiresMatchingLectureCourseAndNonblankLearningText() {
        var unit = new TextUnit();
        unit.setContent("Teach Java");
        assertThat(ContentExtractionService.isCourseLectureUnitEligibleForOrchestration(null, 1L)).isFalse();
        assertThat(ContentExtractionService.isCourseLectureUnitEligibleForOrchestration(unit, 1L)).isFalse();
        var lecture = new de.tum.cit.aet.artemis.lecture.domain.Lecture();
        unit.setLecture(lecture);
        assertThat(ContentExtractionService.isCourseLectureUnitEligibleForOrchestration(unit, 1L)).isFalse();
        var course = new de.tum.cit.aet.artemis.course.domain.Course();
        course.setId(2L);
        lecture.setCourse(course);
        assertThat(ContentExtractionService.isCourseLectureUnitEligibleForOrchestration(unit, 1L)).isFalse();
        course.setId(1L);
        assertThat(ContentExtractionService.isCourseLectureUnitEligibleForOrchestration(unit, 1L)).isTrue();
        unit.setContent(" ");
        assertThat(ContentExtractionService.isCourseLectureUnitEligibleForOrchestration(unit, 1L)).isFalse();
    }

    @Test
    void persistedQuizReloadsFullInstructorQuestionsInsteadOfUsingThinEntity() {
        var thin = new QuizExercise();
        thin.setId(9L);
        thin.setTitle("Thin");
        var loaded = new QuizExercise();
        loaded.setId(9L);
        loaded.setTitle("Loaded quiz");
        var question = new de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion();
        question.setTitle("Question");
        question.setText("Text");
        loaded.setQuizQuestions(List.of(question));
        org.mockito.Mockito.when(quizExerciseRepository.findByIdWithQuestionsElseThrow(9L)).thenReturn(loaded);
        var result = contentExtractionService.extractContent(thin);
        assertThat(result.title()).isEqualTo("Loaded quiz");
        assertThat(result.extractedLearningText()).isEqualTo("Question 1: Question\nText");
        assertThat(result.metadata()).containsEntry("questionCount", "1");
    }

    @Test
    void explicitRawExtractionPreservesTextForAllNarrativeLearningObjectTypes() {
        var text = new TextExercise();
        text.setProblemStatement(" Statement ");
        text.setExampleSolution(" Solution ");
        assertThat(contentExtractionService.extractContent(text, false).extractedLearningText()).isEqualTo(" Statement \n\nExample solution:\nSolution");
        var model = new ModelingExercise();
        model.setProblemStatement(" Model statement ");
        assertThat(contentExtractionService.extractContent(model, false).extractedLearningText()).isEqualTo(" Model statement ");
        var file = new FileUploadExercise();
        file.setProblemStatement(" Upload statement ");
        assertThat(contentExtractionService.extractContent(file, false).extractedLearningText()).isEqualTo(" Upload statement ");
        var unit = new TextUnit();
        unit.setContent(" Unit content ");
        var lecture = new de.tum.cit.aet.artemis.lecture.domain.Lecture();
        unit.setLecture(lecture);
        assertThat(contentExtractionService.extractContent(unit, false).extractedLearningText()).isEqualTo(" Unit content ");
    }
}
