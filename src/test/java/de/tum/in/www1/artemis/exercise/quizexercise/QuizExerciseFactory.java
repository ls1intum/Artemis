package de.tum.in.www1.artemis.exercise.quizexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.enumeration.ScoringType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.service.FilePathService;

/**
 * Factory for creating QuizExercises and related objects.
 */
public class QuizExerciseFactory {

    /**
     * Creates a quiz exercise with the given dates and adds it to the course.
     * The quiz consist of one multiple choice, one drag and drop, and one short answer question.
     *
     * @param course      The course the quiz should be added to.
     * @param releaseDate The release date of the quiz.
     * @param dueDate     The due date of the quiz.
     * @param quizMode    The quiz mode used. SYNCHRONIZED, BATCHED, or INDIVIDUAL.
     * @return The created quiz.
     */
    @NotNull
    public static QuizExercise createQuiz(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        QuizExercise quizExercise = generateQuizExercise(releaseDate, dueDate, quizMode, course);
        addQuestionsToQuizExercise(quizExercise);

        return quizExercise;
    }

    /**
     * Creates a quiz exercise for an exam with the given dates and adds it to the course.
     * The quiz consist of one multiple choice, one drag and drop, and one short answer question.
     *
     * @param exerciseGroup Exercise group of an exam to which the quiz should be added.
     * @return The created quiz.
     */
    @NotNull
    public static QuizExercise createQuizForExam(ExerciseGroup exerciseGroup) {
        QuizExercise quizExercise = generateQuizExerciseForExam(exerciseGroup);
        addQuestionsToQuizExercise(quizExercise);

        return quizExercise;
    }

    /**
     * Adds different types of questions to the given quiz.
     * One multiple choice, one drag and drop, and one short answer question is added to the quiz.
     * The grading instructions are set to null.
     *
     * @param quizExercise The quiz to which questions should be added.
     */
    public static void addQuestionsToQuizExercise(QuizExercise quizExercise) {
        quizExercise.addQuestions(createMultipleChoiceQuestion());
        quizExercise.addQuestions(createDragAndDropQuestion());
        quizExercise.addQuestions(createShortAnswerQuestion());
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.setGradingInstructions(null);
    }

    /**
     * Creates a short answer question with score 2 and 2 different spot-solution mappings.
     * The scoring type of the question is proportional without penalty.
     *
     * @return The created short answer question.
     */
    @NotNull
    public static ShortAnswerQuestion createShortAnswerQuestion() {
        ShortAnswerQuestion sa = (ShortAnswerQuestion) new ShortAnswerQuestion().title("SA").score(2).text("This is a long answer text");
        sa.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        // TODO: we should test different values here
        sa.setMatchLetterCase(true);
        sa.setSimilarityValue(100);

        var shortAnswerSpot1 = new ShortAnswerSpot().spotNr(0).width(1);
        shortAnswerSpot1.setTempID(generateTempId());
        var shortAnswerSpot2 = new ShortAnswerSpot().spotNr(2).width(2);
        shortAnswerSpot2.setTempID(generateTempId());
        sa.getSpots().add(shortAnswerSpot1);
        sa.getSpots().add(shortAnswerSpot2);

        var shortAnswerSolution1 = new ShortAnswerSolution().text("is");
        shortAnswerSolution1.setTempID(generateTempId());
        var shortAnswerSolution2 = new ShortAnswerSolution().text("long");
        shortAnswerSolution2.setTempID(generateTempId());
        sa.addSolution(shortAnswerSolution1);
        // also invoke remove once
        sa.removeSolution(shortAnswerSolution1);
        sa.addSolution(shortAnswerSolution1);
        sa.addSolution(shortAnswerSolution2);

        var mapping1 = new ShortAnswerMapping().spot(sa.getSpots().get(0)).solution(sa.getSolutions().get(0));

        var mapping2 = new ShortAnswerMapping().spot(sa.getSpots().get(1)).solution(sa.getSolutions().get(1));
        sa.addCorrectMapping(mapping1);
        assertThat(sa).isEqualTo(mapping1.getQuestion());
        sa.removeCorrectMapping(mapping1);
        sa.addCorrectMapping(mapping1);
        sa.addCorrectMapping(mapping2);
        sa.setExplanation("Explanation");
        sa.setRandomizeOrder(true);
        // invoke some util methods
        sa.copyQuestionId();
        return sa;
    }

    /**
     * Creates a drag and drop question with score 3 and 3 different location-drag item mappings.
     * The scoring type of the question is proportional with penalty.
     *
     * @return The created drag and drop question.
     */
    @NotNull
    public static DragAndDropQuestion createDragAndDropQuestion() {
        DragAndDropQuestion dnd = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(3).text("Q2");
        dnd.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);

        var dropLocation1 = new DropLocation().posX(10d).posY(10d).height(10d).width(10d);
        dropLocation1.setTempID(generateTempId());
        var dropLocation2 = new DropLocation().posX(20d).posY(20d).height(10d).width(10d);
        dropLocation2.setTempID(generateTempId());
        var dropLocation3 = new DropLocation().posX(30d).posY(30d).height(10d).width(10d);
        dropLocation3.setTempID(generateTempId());
        var dropLocation4 = new DropLocation().posX(40d).posY(40d).height(10d).width(10d);
        dropLocation4.setTempID(generateTempId());
        dnd.addDropLocation(dropLocation1);
        // also invoke remove once
        dnd.removeDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation2);
        dnd.addDropLocation(dropLocation3);
        dnd.addDropLocation(dropLocation4);

        var dragItem1 = new DragItem().text("D1");
        dragItem1.setTempID(generateTempId());
        var dragItem2 = new DragItem().pictureFilePath("dragItemImage2.png");
        dragItem2.setTempID(generateTempId());
        var dragItem3 = new DragItem().text("D3");
        dragItem3.setTempID(generateTempId());
        var dragItem4 = new DragItem().pictureFilePath("dragItemImage4.png");
        dragItem4.setTempID(generateTempId());
        dnd.addDragItem(dragItem1);
        assertThat(dragItem1.getQuestion()).isEqualTo(dnd);
        // also invoke remove once
        dnd.removeDragItem(dragItem1);
        dnd.addDragItem(dragItem1);
        dnd.addDragItem(dragItem2);
        dnd.addDragItem(dragItem3);
        dnd.addDragItem(dragItem4);

        var mapping1 = new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1);

        dnd.addCorrectMapping(mapping1);
        dnd.removeCorrectMapping(mapping1);
        dnd.addCorrectMapping(mapping1);
        var mapping2 = new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2);
        dnd.addCorrectMapping(mapping2);
        var mapping3 = new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3);
        dnd.addCorrectMapping(mapping3);
        var mapping4 = new DragAndDropMapping().dragItem(dragItem4).dropLocation(dropLocation4);
        dnd.addCorrectMapping(mapping4);
        dnd.setExplanation("Explanation");
        // invoke some util methods

        dnd.copyQuestionId();

        return dnd;
    }

    /**
     * Generates a temporary id for a quiz exercise using ThreadLocalRandom.
     *
     * @return The generated temporary id.
     */
    public static Long generateTempId() {
        return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    /**
     * Creates a multiple choice question with score 4 and 2 different answer options. One answer is correct and the other one incorrect.
     * The scoring type of the question is all or nothing.
     *
     * @return The created multiple choice question.
     */
    @NotNull
    public static MultipleChoiceQuestion createMultipleChoiceQuestion() {
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4).text("Q1");
        mc.setScoringType(ScoringType.ALL_OR_NOTHING);
        mc.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        mc.setExplanation("Explanation");

        mc.copyQuestionId();
        return mc;
    }

    /**
     * Generates a quiz batch for a quiz exercise using the given start time.
     *
     * @param quizExercise The quiz to which a batch should be added.
     * @param startTime    The start time of the batch.
     * @return The created quiz batch.
     */
    public static QuizBatch generateQuizBatch(QuizExercise quizExercise, ZonedDateTime startTime) {
        var quizBatch = new QuizBatch();
        quizBatch.setQuizExercise(quizExercise);
        quizBatch.setStartTime(startTime);
        return quizBatch;
    }

    /**
     * Generates a quiz exercise without an assessment due date and adds it to the given course.
     *
     * @param releaseDate The release date of the quiz.
     * @param dueDate     The due date of the quiz.
     * @param quizMode    The quiz mode of the quiz. SYNCHRONIZED, BATCHED, or INDIVIDUAL.
     * @param course      The course to which the quiz should be added to.
     * @return The created quiz exercise.
     */
    public static QuizExercise generateQuizExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode, Course course) {
        return generateQuizExercise(releaseDate, dueDate, null, quizMode, course);
    }

    /**
     * Generates a quiz exercise using the passed dates and adds it to the given course.
     *
     * @param releaseDate       The release date of the quiz.
     * @param dueDate           The due date of the quiz.
     * @param assessmentDueDate The assessment due date of the quiz.
     * @param quizMode          The quiz mode of the quiz. SYNCHRONIZED, BATCHED, or INDIVIDUAL.
     * @param course            The course to which the quiz should be added to.
     * @return The created quiz exercise.
     */
    public static QuizExercise generateQuizExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, QuizMode quizMode, Course course) {
        QuizExercise quizExercise = (QuizExercise) ExerciseFactory.populateExercise(new QuizExercise(), releaseDate, dueDate, assessmentDueDate, course);
        quizExercise.setTitle("new quiz");

        quizExercise.setProblemStatement(null);
        quizExercise.setGradingInstructions(null);
        quizExercise.setPresentationScoreEnabled(false);
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setAllowedNumberOfAttempts(1);
        quizExercise.setDuration(10);
        quizExercise.setRandomizeQuestionOrder(true);
        quizExercise.setQuizMode(quizMode);
        if (quizMode == QuizMode.SYNCHRONIZED) {
            quizExercise.setQuizBatches(Set.of(generateQuizBatch(quizExercise, releaseDate)));
        }
        return quizExercise;
    }

    /**
     * Generates a submitted answer for the given quiz question of any type.
     * The correctness of the answer depends on the passed correct value.
     *
     * @param question The quiz question to which a submitted answer should be added.
     * @param correct  True, if the answer should be correct.
     * @return The generated submitted answer.
     */
    public static SubmittedAnswer generateSubmittedAnswerFor(QuizQuestion question, boolean correct) {
        if (question instanceof MultipleChoiceQuestion) {
            var submittedAnswer = new MultipleChoiceSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                if (answerOption.isIsCorrect().equals(correct)) {
                    submittedAnswer.addSelectedOptions(answerOption);
                }
            }
            return submittedAnswer;
        }
        else if (question instanceof DragAndDropQuestion) {
            var submittedAnswer = new DragAndDropSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            DragItem dragItem1 = ((DragAndDropQuestion) question).getDragItems().get(0);
            dragItem1.setQuestion((DragAndDropQuestion) question);

            DragItem dragItem2 = ((DragAndDropQuestion) question).getDragItems().get(1);
            dragItem2.setQuestion((DragAndDropQuestion) question);

            DragItem dragItem3 = ((DragAndDropQuestion) question).getDragItems().get(2);
            dragItem3.setQuestion((DragAndDropQuestion) question);

            DragItem dragItem4 = ((DragAndDropQuestion) question).getDragItems().get(3);
            dragItem4.setQuestion((DragAndDropQuestion) question);

            DropLocation dropLocation1 = ((DragAndDropQuestion) question).getDropLocations().get(0);
            dropLocation1.setQuestion((DragAndDropQuestion) question);

            DropLocation dropLocation2 = ((DragAndDropQuestion) question).getDropLocations().get(1);
            dropLocation2.setQuestion((DragAndDropQuestion) question);

            DropLocation dropLocation3 = ((DragAndDropQuestion) question).getDropLocations().get(2);
            dropLocation3.setQuestion((DragAndDropQuestion) question);

            DropLocation dropLocation4 = ((DragAndDropQuestion) question).getDropLocations().get(3);
            dropLocation4.setQuestion((DragAndDropQuestion) question);

            if (correct) {
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem4).dropLocation(dropLocation4));
            }
            else {
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation3));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation4));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation1));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem4).dropLocation(dropLocation2));
            }

            return submittedAnswer;
        }
        else if (question instanceof ShortAnswerQuestion) {
            var submittedAnswer = new ShortAnswerSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var spot : ((ShortAnswerQuestion) question).getSpots()) {
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spot);
                var correctText = ((ShortAnswerQuestion) question).getCorrectSolutionForSpot(spot).iterator().next().getText();
                if (correct) {
                    submittedText.setText(correctText);
                }
                else {
                    submittedText.setText(correctText.toUpperCase());
                }
                submittedAnswer.addSubmittedTexts(submittedText);
                // also invoke remove once
                submittedAnswer.removeSubmittedTexts(submittedText);
                submittedAnswer.addSubmittedTexts(submittedText);
            }
            return submittedAnswer;
        }
        return null;
    }

    /**
     * Generates a quiz exercise for an exam with the duration of 10 seconds and randomized question order.
     *
     * @param exerciseGroup The exam exercise group to which the quiz should be added.
     * @return The created exam quiz exercise.
     */
    public static QuizExercise generateQuizExerciseForExam(ExerciseGroup exerciseGroup) {
        var quizExercise = (QuizExercise) ExerciseFactory.populateExerciseForExam(new QuizExercise(), exerciseGroup);

        quizExercise.setProblemStatement(null);
        quizExercise.setGradingInstructions(null);
        quizExercise.setPresentationScoreEnabled(false);
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setAllowedNumberOfAttempts(1);
        quizExercise.setDuration(10);
        quizExercise.setQuizPointStatistic(new QuizPointStatistic());
        quizExercise.setRandomizeQuestionOrder(true);

        return quizExercise;
    }

    /**
     * Generates quiz exercise for an exam with the duration of 10 seconds and randomized question order.
     *
     * @param exerciseGroup The exam exercise group to which the quiz exercise should be added.
     * @param title         The title which is used to set the quiz title together with a 3 character random UUID suffix.
     * @return The generated quiz exercise.
     */
    public static QuizExercise generateQuizExerciseForExam(ExerciseGroup exerciseGroup, String title) {
        var quizExercise = generateQuizExerciseForExam(exerciseGroup);
        quizExercise.setTitle(title + UUID.randomUUID().toString().substring(0, 3));
        return quizExercise;
    }

    /**
     * Adds different types of questions to the given quiz.
     * One multiple choice, one drag and drop, one short answer, and one single choice question is added to the quiz.
     * The grading instructions are set to null.
     *
     * @param quizExercise The quiz to which questions should be added.
     */
    public static void addAllQuestionTypesToQuizExercise(QuizExercise quizExercise) {
        quizExercise.addQuestions(createMultipleChoiceQuestionWithAllTypesOfAnswerOptions());
        quizExercise.addQuestions(createDragAndDropQuestionWithAllTypesOfMappings());
        quizExercise.addQuestions(createShortAnswerQuestionWithRealisticText());
        quizExercise.addQuestions(createSingleChoiceQuestion());
    }

    /**
     * Creates a short answer question with "This [-spot0] a [-spot 2] answer text" text.
     *
     * @return The created short answer question.
     */
    public static ShortAnswerQuestion createShortAnswerQuestionWithRealisticText() {
        var shortAnswerQuestion = createShortAnswerQuestion();
        shortAnswerQuestion.setText("This [-spot0] a [-spot 2] answer text");
        return shortAnswerQuestion;
    }

    /**
     * Creates a drag and drop question with score 5 and 3 different location-drag item mappings.
     * Fourth drag item is invalid and fifth one has a picture file.
     * The scoring type of the question is proportional with penalty.
     *
     * @return The created drag and drop question.
     */
    public static DragAndDropQuestion createDragAndDropQuestionWithAllTypesOfMappings() {
        DragAndDropQuestion dnd = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(3).text("Q2");
        dnd.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);

        var dropLocation1 = new DropLocation().posX(10d).posY(10d).height(10d).width(10d);
        dropLocation1.setTempID(generateTempId());
        var dropLocation2 = new DropLocation().posX(20d).posY(20d).height(10d).width(10d);
        dropLocation2.setTempID(generateTempId());
        var dropLocation3 = new DropLocation().posX(30d).posY(30d).height(10d).width(10d);
        dropLocation3.setInvalid(true);
        dropLocation3.setTempID(generateTempId());
        var dropLocation4 = new DropLocation().posX(40d).posY(40d).height(10d).width(10d);
        dropLocation4.setTempID(generateTempId());
        var dropLocation5 = new DropLocation().posX(50d).posY(50d).height(10d).width(10d);
        dropLocation5.setTempID(generateTempId());
        dnd.addDropLocation(dropLocation1);
        // also invoke remove once
        dnd.removeDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation2);
        dnd.addDropLocation(dropLocation3);
        dnd.addDropLocation(dropLocation4);
        dnd.addDropLocation(dropLocation5);

        var dragItem1 = new DragItem().text("D1");
        dragItem1.setTempID(generateTempId());
        var dragItem2 = new DragItem().text("D2");
        dragItem2.setTempID(generateTempId());
        var dragItem3 = new DragItem().text("D3");
        dragItem3.setTempID(generateTempId());
        var dragItem4 = new DragItem().text("invalid drag item");
        dragItem4.setTempID(generateTempId());
        try {
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/attachment/placeholder.jpg"),
                    FilePathService.getDragItemFilePath().resolve("10").resolve("drag_item.jpg").toFile());
        }
        catch (IOException ex) {
            fail("Failed while copying test attachment files", ex);
        }
        var dragItem5 = new DragItem().pictureFilePath("/api/files/drag-and-drop/drag-items/10/drag_item.jpg");
        dragItem4.setInvalid(true);
        dnd.addDragItem(dragItem1);
        assertThat(dragItem1.getQuestion()).isEqualTo(dnd);
        // also invoke remove once
        dnd.removeDragItem(dragItem1);
        dnd.addDragItem(dragItem1);
        dnd.addDragItem(dragItem2);
        dnd.addDragItem(dragItem3);
        dnd.addDragItem(dragItem4);
        dnd.addDragItem(dragItem5);

        var mapping1 = new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1);

        dnd.addCorrectMapping(mapping1);
        dnd.removeCorrectMapping(mapping1);
        dnd.addCorrectMapping(mapping1);
        var mapping2 = new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2);
        dnd.addCorrectMapping(mapping2);
        var mapping3 = new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3);
        dnd.addCorrectMapping(mapping3);
        var mapping4 = new DragAndDropMapping().dragItem(dragItem4).dropLocation(dropLocation4);
        dnd.addCorrectMapping(mapping4);
        var mapping5 = new DragAndDropMapping().dragItem(dragItem5).dropLocation(dropLocation5);
        dnd.addCorrectMapping(mapping5);
        dnd.setExplanation("Explanation");
        return dnd;
    }

    /**
     * Creates a multiple choice question with score 4 and 5 different answer options. 2 answers are correct and the other 3 incorrect.
     * One answer option is invalid.
     * The scoring type of the question is all or nothing.
     *
     * @return The created multiple choice question.
     */
    @NotNull
    public static MultipleChoiceQuestion createMultipleChoiceQuestionWithAllTypesOfAnswerOptions() {
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4).text("Q1");
        mc.setScoringType(ScoringType.ALL_OR_NOTHING);
        mc.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isInvalid(true).isCorrect(false));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("E").hint("H5").explanation("E5").isCorrect(false));
        return mc;
    }

    /**
     * Creates a single choice question with 2 answer options.
     * The scoring type of the question is all or nothing.
     *
     * @return The created single choice question.
     */
    @NotNull
    public static MultipleChoiceQuestion createSingleChoiceQuestion() {
        var singleChoiceQuestion = createMultipleChoiceQuestion();
        singleChoiceQuestion.setSingleChoice(true);
        return singleChoiceQuestion;
    }

    /**
     * Generates a submission for the given quiz exercise. The quiz should have at least 3 questions!
     * A submitted answer is added to each of the first 3 questions.
     * The result of each answer depends on the given student id.
     *
     * @param quizExercise   The quiz to which the submission should be added.
     * @param studentID      The id of the student participating in the quiz.
     * @param submitted      True, if the submission should be submitted.
     * @param submissionDate The submission date.
     * @return The created quiz submission.
     */
    public static QuizSubmission generateSubmissionForThreeQuestions(QuizExercise quizExercise, int studentID, boolean submitted, ZonedDateTime submissionDate) {
        QuizSubmission quizSubmission = new QuizSubmission();
        QuizQuestion quizQuestion1 = quizExercise.getQuizQuestions().get(0);
        QuizQuestion quizQuestion2 = quizExercise.getQuizQuestions().get(1);
        QuizQuestion quizQuestion3 = quizExercise.getQuizQuestions().get(2);
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion1, studentID % 2 == 0));
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion2, studentID % 3 == 0));
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion3, studentID % 4 == 0));
        quizSubmission.submitted(submitted);
        quizSubmission.submissionDate(submissionDate);

        return quizSubmission;
    }

    /**
     * Generates a submitted answer for the given quiz question of any type.
     * The answer is only partially correct.
     *
     * @param question The quiz question to which a submitted answer should be added.
     * @return The generated submitted answer.
     */
    public static SubmittedAnswer generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(QuizQuestion question) {
        if (question instanceof MultipleChoiceQuestion) {
            var submittedAnswer = new MultipleChoiceSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                submittedAnswer.addSelectedOptions(answerOption);
            }
            return submittedAnswer;
        }
        else if (question instanceof DragAndDropQuestion) {
            var submittedAnswer = new DragAndDropSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            DragItem dragItem1 = ((DragAndDropQuestion) question).getDragItems().get(0);
            dragItem1.setQuestion((DragAndDropQuestion) question);

            DragItem dragItem2 = ((DragAndDropQuestion) question).getDragItems().get(1);
            dragItem2.setQuestion((DragAndDropQuestion) question);

            DragItem dragItem3 = ((DragAndDropQuestion) question).getDragItems().get(2);
            dragItem3.setQuestion((DragAndDropQuestion) question);

            DropLocation dropLocation1 = ((DragAndDropQuestion) question).getDropLocations().get(0);
            dropLocation1.setQuestion((DragAndDropQuestion) question);

            DropLocation dropLocation2 = ((DragAndDropQuestion) question).getDropLocations().get(1);
            dropLocation2.setQuestion((DragAndDropQuestion) question);

            DropLocation dropLocation3 = ((DragAndDropQuestion) question).getDropLocations().get(2);
            dropLocation3.setQuestion((DragAndDropQuestion) question);

            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation3));
            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation2));

            return submittedAnswer;
        }
        else if (question instanceof ShortAnswerQuestion) {
            var submittedAnswer = new ShortAnswerSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var spot : ((ShortAnswerQuestion) question).getSpots()) {
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spot);
                var correctText = ((ShortAnswerQuestion) question).getCorrectSolutionForSpot(spot).iterator().next().getText();
                if (spot.getSpotNr() == 2) {
                    submittedText.setText(correctText);
                }
                else {
                    submittedText.setText("wrong submitted text");
                }
                submittedAnswer.addSubmittedTexts(submittedText);
                // also invoke remove once
                submittedAnswer.removeSubmittedTexts(submittedText);
                submittedAnswer.addSubmittedTexts(submittedText);
            }
            return submittedAnswer;
        }
        return null;
    }

    /**
     * Generate a submission with all or none options of a multiple choice questions selected.
     * For other question typed, incorrect submitted answers are added.
     *
     * @param quizExercise     The quiz to which the submission should be added.
     * @param submitted        True, if the submission is submitted.
     * @param submissionDate   The date the submission is submitted.
     * @param selectEverything True, if every answer option should be selected. Otherwise, none are selected.
     */
    public static QuizSubmission generateSpecialSubmissionWithResult(QuizExercise quizExercise, boolean submitted, ZonedDateTime submissionDate, boolean selectEverything) {
        QuizSubmission quizSubmission = new QuizSubmission();

        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                var submittedAnswer = new MultipleChoiceSubmittedAnswer();
                submittedAnswer.setQuizQuestion(question);
                if (selectEverything) {
                    for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                        submittedAnswer.addSelectedOptions(answerOption);
                    }
                }
                quizSubmission.addSubmittedAnswers(submittedAnswer);

            }
            else {
                quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(question, false));
            }
        }
        quizSubmission.submitted(submitted);
        quizSubmission.submissionDate(submissionDate);

        return quizSubmission;
    }

    /**
     * Creates a titled quiz exercise with all types of questions for an exam.
     * The quiz includes one multiple choice, one drag and drop, one short answer, and single choice question.
     *
     * @param exerciseGroup The exam exercise group to which the quiz exercise should be added.
     * @param title         The title which is used to set the quiz title together with a 3 character random UUID suffix.
     * @return The created exam quiz exercise.
     */
    @NotNull
    public static QuizExercise createQuizWithAllQuestionTypesForExam(ExerciseGroup exerciseGroup, String title) {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExerciseForExam(exerciseGroup, title);
        addAllQuestionTypesToQuizExercise(quizExercise);
        return quizExercise;
    }

    /**
     * Creates a quiz submissions and adds a submitted answer to it.
     *
     * @param quizQuestion The quiz question to which a submission should be added.
     * @param correct      True if, the generated answer should be correct.
     * @return The created quiz submission.
     */
    public static QuizSubmission generateQuizSubmissionWithSubmittedAnswer(QuizQuestion quizQuestion, boolean correct) {
        QuizSubmission quizSubmission = ParticipationFactory.generateQuizSubmission(true);
        quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerFor(quizQuestion, correct));

        return quizSubmission;
    }

    /**
     * Sets quiz questions associated to quiz submission answers to null.
     *
     * @param quizSubmission The quiz submission with answer.
     */
    public static void setQuizQuestionToNull(QuizSubmission quizSubmission) {
        quizSubmission.getSubmittedAnswers().forEach(answer -> answer.setQuizQuestion(null));
    }

    /**
     * Sets quiz question ids associated to quiz submission answers to null.
     *
     * @param quizSubmission The quiz submission with answer.
     */
    public static void setQuizQuestionsIdToNull(QuizSubmission quizSubmission) {
        quizSubmission.getSubmittedAnswers().forEach(answer -> answer.getQuizQuestion().setId(null));
    }

    /**
     * Creates a quiz group with the given name.
     *
     * @param name The name of the quiz group.
     * @return The created quiz group.
     */
    @NotNull
    public static QuizGroup createQuizGroup(String name) {
        QuizGroup quizGroup = new QuizGroup();
        quizGroup.setName(name);
        return quizGroup;
    }

    /**
     * Creates a multiple choice question with the given title and quiz group.
     *
     * @param title     The title of the quiz question.
     * @param quizGroup The group of the quiz question.
     * @return The created multiple choice question.
     */
    @NotNull
    public static MultipleChoiceQuestion createMultipleChoiceQuestionWithTitleAndGroup(String title, QuizGroup quizGroup) {
        MultipleChoiceQuestion quizQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        setQuizQuestionsTitleAndGroup(quizQuestion, title, quizGroup);
        return quizQuestion;
    }

    /**
     * Creates a drag and drop question with the given title and quiz group.
     *
     * @param title     The title of the quiz question.
     * @param quizGroup The group of the quiz question.
     * @return The created drag and drop question.
     */
    @NotNull
    public static DragAndDropQuestion createDragAndDropQuestionWithTitleAndGroup(String title, QuizGroup quizGroup) {
        DragAndDropQuestion quizQuestion = QuizExerciseFactory.createDragAndDropQuestion();
        setQuizQuestionsTitleAndGroup(quizQuestion, title, quizGroup);
        return quizQuestion;
    }

    /**
     * Creates a short answer question with the given title and quiz group.
     *
     * @param title     The title of the quiz question.
     * @param quizGroup The group of the quiz question.
     * @return The created short answer question.
     */
    @NotNull
    public static ShortAnswerQuestion createShortAnswerQuestionWithTitleAndGroup(String title, QuizGroup quizGroup) {
        ShortAnswerQuestion quizQuestion = QuizExerciseFactory.createShortAnswerQuestion();
        setQuizQuestionsTitleAndGroup(quizQuestion, title, quizGroup);
        return quizQuestion;
    }

    /**
     * Sets the title and group of the quiz question.
     *
     * @param quizQuestion The quiz question to be updated.
     * @param title        The new title of the quiz question.
     * @param quizGroup    The new group of the quiz question.
     */
    private static void setQuizQuestionsTitleAndGroup(QuizQuestion quizQuestion, String title, QuizGroup quizGroup) {
        quizQuestion.setTitle(title);
        quizQuestion.setQuizGroup(quizGroup);
    }
}
