package de.tum.in.www1.artemis.exercise.quizexercise;

import static de.tum.in.www1.artemis.service.exam.StudentExamService.isContentEqualTo;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.domain.quiz.compare.DnDMapping;
import de.tum.in.www1.artemis.domain.quiz.compare.SAMapping;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.participation.ParticipationFactory;

class QuizComparisonTest {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Test
    void compareQuizSubmissionWithNull() {
        // Test null values
        QuizSubmission submission = new QuizSubmission();
        assertThat(isContentEqualTo((QuizSubmission) null, null)).isTrue();
        assertThat(isContentEqualTo(null, submission)).isFalse();
        assertThat(isContentEqualTo(submission, null)).isFalse();
    }

    @Test
    void testCompareSubmissionsWithNullQuestionAndIds() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, QuizMode.INDIVIDUAL);
        List<QuizQuestion> quizQuestions = setAllQuizQuestionIds(quizExercise);

        // create a submission for each questionType
        QuizSubmission submission1 = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(1), true);
        QuizSubmission submission2 = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(1), true);
        QuizSubmission submission3 = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(1), true);
        QuizSubmission submission4 = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(1), false);

        QuizExerciseFactory.setQuizQuestionToNull(submission1);
        QuizExerciseFactory.setQuizQuestionsIdToNull(submission3);

        // comparison should still work, even if quiz question or the quiz question id is null
        Assertions.assertThat(isContentEqualTo(submission1, submission2)).isTrue();
        Assertions.assertThat(isContentEqualTo(submission1, submission3)).isTrue();
        Assertions.assertThat(isContentEqualTo(submission3, submission2)).isTrue();

        // submission4 is different from all other submissions
        Assertions.assertThat(isContentEqualTo(submission1, submission4)).isFalse();
        Assertions.assertThat(isContentEqualTo(submission2, submission4)).isFalse();
        Assertions.assertThat(isContentEqualTo(submission3, submission4)).isFalse();
    }

    @Test
    void testCompareSubmissionsOfDifferentQuestionTypeSameId() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, QuizMode.INDIVIDUAL);
        List<QuizQuestion> quizQuestions = setAllQuizQuestionIds(quizExercise);
        Long id1 = quizQuestions.get(0).getId();

        // create a submission for each questionType
        QuizSubmission mcSubmission = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(0), false);
        QuizSubmission dndSubmission = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(1), false);
        QuizSubmission saSubmission = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(2), false);

        // change Ids so that all questions have the same id
        quizQuestions.get(1).setId(id1);
        quizQuestions.get(2).setId(id1);

        Assertions.assertThat(isContentEqualTo(mcSubmission, dndSubmission)).isFalse();
        Assertions.assertThat(isContentEqualTo(mcSubmission, saSubmission)).isFalse();
        Assertions.assertThat(isContentEqualTo(saSubmission, dndSubmission)).isFalse();
    }

    @Test
    void testCompareSubmissionsWithEmptySubmission() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, QuizMode.INDIVIDUAL);
        List<QuizQuestion> quizQuestions = setAllQuizQuestionIds(quizExercise);

        // create a submission for each questionType
        QuizSubmission mcSubmission = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(0), true);
        QuizSubmission dndSubmission = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(1), true);
        QuizSubmission saSubmission = QuizExerciseFactory.generateQuizSubmissionWithSubmittedAnswer(quizQuestions.get(2), true);

        QuizSubmission submissionWithNoAnswer = ParticipationFactory.generateQuizSubmission(true);

        Assertions.assertThat(isContentEqualTo(mcSubmission, submissionWithNoAnswer)).isFalse();
        Assertions.assertThat(isContentEqualTo(dndSubmission, submissionWithNoAnswer)).isFalse();
        Assertions.assertThat(isContentEqualTo(saSubmission, submissionWithNoAnswer)).isFalse();
    }

    @Test
    void compareCourseQuizSubmittedAnswers() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, QuizMode.INDIVIDUAL);

        createSubmissionsForQuizQuestionsAndAssert(quizExercise);
    }

    @Test
    void compareExamQuizSubmittedAnswers() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam = ExamFactory.generateExamWithExerciseGroup(course, true);
        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exam.getExerciseGroups().get(0));

        createSubmissionsForQuizQuestionsAndAssert(quizExercise);
    }

    void createSubmissionsForQuizQuestionsAndAssert(QuizExercise quizExercise) {
        long id = 1L;
        for (var question : quizExercise.getQuizQuestions()) {
            id = setQuizQuestionIds(question, id);

            var submittedAnswer1 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);
            var submittedAnswer2 = QuizExerciseFactory.generateSubmittedAnswerFor(question, false);
            var submittedAnswer3 = QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question);
            var submittedAnswer4 = QuizExerciseFactory.generateSubmittedAnswerFor(question, false);

            assertThat(isContentEqualTo(submittedAnswer1, submittedAnswer1)).isTrue();
            assertThat(isContentEqualTo(submittedAnswer1, submittedAnswer2)).isFalse();
            assertThat(isContentEqualTo(submittedAnswer2, submittedAnswer1)).isFalse();
            assertThat(isContentEqualTo(submittedAnswer1, submittedAnswer3)).isFalse();
            assertThat(isContentEqualTo(submittedAnswer1, submittedAnswer4)).isFalse();

            assertThat(isContentEqualTo(submittedAnswer2, submittedAnswer3)).isFalse();
            assertThat(isContentEqualTo(submittedAnswer2, submittedAnswer4)).isTrue();
            assertThat(isContentEqualTo(submittedAnswer4, submittedAnswer2)).isTrue();

            assertThat(isContentEqualTo(submittedAnswer3, submittedAnswer4)).isFalse();
        }
    }

    @Test
    void compareQuizSubmittedAnswersWithChangedAnswers() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, QuizMode.INDIVIDUAL);

        long id = 1L;
        for (var question : quizExercise.getQuizQuestions()) {
            id = setQuizQuestionIds(question, id);

            var submittedAnswer1 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);
            var submittedAnswer2 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);

            if (submittedAnswer2 instanceof MultipleChoiceSubmittedAnswer changedSubmittedAnswer) {
                Set<AnswerOption> answerOptions = changedSubmittedAnswer.getSelectedOptions();
                assertThat(answerOptions.size()).isEqualTo(1);

                Set<AnswerOption> notSelectedOption = ((MultipleChoiceQuestion) question).getAnswerOptions().stream().filter(option -> !option.isIsCorrect())
                        .collect(Collectors.toSet());

                // set the unselected option
                changedSubmittedAnswer.setSelectedOptions(notSelectedOption);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // reset
                changedSubmittedAnswer.setSelectedOptions(answerOptions);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isTrue();

            }
            else if (submittedAnswer2 instanceof DragAndDropSubmittedAnswer changedSubmittedAnswer) {
                List<DragAndDropMapping> dragAndDropMappings = changedSubmittedAnswer.getMappings().stream().toList();
                assertThat(dragAndDropMappings.size()).isEqualTo(4);

                DragAndDropMapping mapping1 = dragAndDropMappings.get(0);
                DragAndDropMapping mapping2 = dragAndDropMappings.get(1);
                DragAndDropMapping mapping3 = dragAndDropMappings.get(2);

                DragItem dragItem1 = mapping1.getDragItem();
                DropLocation dropLocation1 = mapping1.getDropLocation();

                // change the drag item of one mapping
                mapping1.setDragItem(mapping2.getDragItem());
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // mapping 1 and 2 have their drag items switched
                mapping2.setDragItem(dragItem1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // change all 3 drag items
                mapping2.setDragItem(mapping3.getDragItem());
                mapping3.setDragItem(dragItem1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // reset
                mapping3.setDragItem(mapping2.getDragItem());
                mapping2.setDragItem(mapping1.getDragItem());
                mapping1.setDragItem(dragItem1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isTrue();

                // change the drop location of one mapping
                mapping1.setDropLocation(mapping2.getDropLocation());
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // mapping 1 and 2 have their drop locations switched
                mapping2.setDropLocation(dropLocation1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // change all 3 drop locations
                mapping2.setDropLocation(mapping3.getDropLocation());
                mapping3.setDropLocation(dropLocation1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // reset
                mapping3.setDropLocation(mapping2.getDropLocation());
                mapping2.setDropLocation(mapping1.getDropLocation());
                mapping1.setDropLocation(dropLocation1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isTrue();

            }
            else if (submittedAnswer2 instanceof ShortAnswerSubmittedAnswer changedSubmittedAnswer) {
                List<ShortAnswerSubmittedText> shortAnswerSubmittedTexts = changedSubmittedAnswer.getSubmittedTexts().stream().toList();
                assertThat(shortAnswerSubmittedTexts.size()).isEqualTo(2);

                ShortAnswerSubmittedText submittedText1 = shortAnswerSubmittedTexts.get(0);
                ShortAnswerSubmittedText submittedText2 = shortAnswerSubmittedTexts.get(1);
                ShortAnswerSpot spot1 = submittedText1.getSpot();
                String answerText1 = submittedText1.getText();

                // change the spot of one mapping
                submittedText1.setSpot(submittedText2.getSpot());
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // switch spots
                submittedText1.setSpot(submittedText2.getSpot());
                submittedText2.setSpot(spot1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // reset
                submittedText2.setSpot(submittedText1.getSpot());
                submittedText1.setSpot(spot1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isTrue();

                // change first submitted text
                submittedText1.setText("some new text, not yet used");
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                submittedText1.setText(submittedText2.getText());
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // switch the texts
                submittedText2.setText(answerText1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // reset
                submittedText2.setText(submittedText1.getText());
                submittedText1.setText(answerText1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isTrue();
            }
        }
    }

    @Test
    void compareQuizSubmittedAnswersWithAddedAnswers() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, QuizMode.INDIVIDUAL);

        long id = 1L;
        for (var question : quizExercise.getQuizQuestions()) {
            id = setQuizQuestionIds(question, id);

            var submittedAnswer1 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);
            var submittedAnswer2 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);

            if (submittedAnswer2 instanceof MultipleChoiceSubmittedAnswer changedSubmittedAnswer && submittedAnswer1 instanceof MultipleChoiceSubmittedAnswer originalAnswer) {
                Set<AnswerOption> answerOptions = changedSubmittedAnswer.getSelectedOptions();
                assertThat(answerOptions.size()).isEqualTo(1);

                Set<AnswerOption> notSelectedOption = ((MultipleChoiceQuestion) question).getAnswerOptions().stream().filter(option -> !option.isIsCorrect())
                        .collect(Collectors.toSet());
                // add the not selected option
                notSelectedOption.forEach(changedSubmittedAnswer::addSelectedOptions);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                // also add to the original
                notSelectedOption.forEach(originalAnswer::addSelectedOptions);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isTrue();

                // reset submitted answers, compare with the unchanged original
                changedSubmittedAnswer.setSelectedOptions(answerOptions);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isTrue();

            }
            else if (submittedAnswer2 instanceof DragAndDropSubmittedAnswer changedSubmittedAnswer && submittedAnswer1 instanceof DragAndDropSubmittedAnswer originalAnswer) {
                List<DragAndDropMapping> dragAndDropMappings = changedSubmittedAnswer.getMappings().stream().toList();
                assertThat(dragAndDropMappings.size()).isEqualTo(4);

                DragAndDropMapping mapping1 = dragAndDropMappings.get(0);
                DragAndDropMapping mapping2 = dragAndDropMappings.get(1);

                // filter for mapping1 and mapping2 that should get removed
                var temporaryRemoved = originalAnswer.getMappings().stream()
                        .filter(mapping -> mapping.getDragItem().equals(mapping1.getDragItem()) || mapping.getDragItem().equals(mapping2.getDragItem()))
                        .collect(Collectors.toSet());
                assertThat(temporaryRemoved.size()).isEqualTo(2);

                // remove mapping1 and mapping2 so that they can be added later
                temporaryRemoved.forEach(originalAnswer::removeMappings);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.removeMappings(mapping1);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.removeMappings(mapping2);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isTrue();

                // start adding the mappings into the changed map
                changedSubmittedAnswer.addMappings(mapping1);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.addMappings(mapping2);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                // now both answers have the same mappings again
                temporaryRemoved.forEach(originalAnswer::addMappings);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isTrue();

            }
            else if (submittedAnswer2 instanceof ShortAnswerSubmittedAnswer changedSubmittedAnswer && submittedAnswer1 instanceof ShortAnswerSubmittedAnswer originalAnswer) {
                var shortAnswerMappings = changedSubmittedAnswer.getSubmittedTexts().stream().toList();
                assertThat(shortAnswerMappings.size()).isEqualTo(2);

                ShortAnswerSubmittedText submittedText1 = shortAnswerMappings.get(0);
                ShortAnswerSubmittedText submittedText2 = shortAnswerMappings.get(1);

                var temporaryRemoved = originalAnswer.getSubmittedTexts().stream()
                        .filter(mapping -> mapping.getSpot().equals(submittedText1.getSpot()) || mapping.getSpot().equals(submittedText2.getSpot())).collect(Collectors.toSet());
                assertThat(temporaryRemoved.size()).isEqualTo(2);

                // remove submittedText1 and submittedText2 so that they can be added later
                temporaryRemoved.forEach(originalAnswer::removeSubmittedTexts);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.removeSubmittedTexts(submittedText1);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.removeSubmittedTexts(submittedText2);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isTrue();

                // start adding the text
                changedSubmittedAnswer.addSubmittedTexts(submittedText1);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.addSubmittedTexts(submittedText2);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isFalse();

                // now both answers have the same texts again
                temporaryRemoved.forEach(originalAnswer::addSubmittedTexts);
                assertThat(isContentEqualTo(originalAnswer, changedSubmittedAnswer)).isTrue();
            }

        }
    }

    @Test
    void compareQuizSubmittedAnswersWithRemovedAnswers() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, QuizMode.INDIVIDUAL);

        long id = 1L;
        for (var question : quizExercise.getQuizQuestions()) {
            id = setQuizQuestionIds(question, id);

            var submittedAnswer1 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);
            var submittedAnswer2 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);

            if (submittedAnswer2 instanceof MultipleChoiceSubmittedAnswer submittedAnswer) {
                Set<AnswerOption> answerOptions = submittedAnswer.getSelectedOptions();
                assertThat(answerOptions.size()).isEqualTo(1);

                // remove all selected options
                submittedAnswer.setSelectedOptions(Set.of());
                assertThat(isContentEqualTo(submittedAnswer1, submittedAnswer)).isFalse();

                // add additional optionals
                var allOptions = new HashSet<>(((MultipleChoiceQuestion) question).getAnswerOptions());
                submittedAnswer.setSelectedOptions(allOptions);
                assertThat(isContentEqualTo(submittedAnswer1, submittedAnswer)).isFalse();

                // reset
                submittedAnswer.setSelectedOptions(answerOptions);
                assertThat(isContentEqualTo(submittedAnswer1, submittedAnswer)).isTrue();

            }
            else if (submittedAnswer2 instanceof DragAndDropSubmittedAnswer changedSubmittedAnswer) {
                List<DragAndDropMapping> dragAndDropMappings = changedSubmittedAnswer.getMappings().stream().toList();
                assertThat(dragAndDropMappings.size()).isEqualTo(4);
                DragAndDropMapping mapping1 = dragAndDropMappings.get(0);
                DragAndDropMapping mapping2 = dragAndDropMappings.get(1);
                DragAndDropMapping mapping3 = dragAndDropMappings.get(1);

                // remove the first mapping
                changedSubmittedAnswer.removeMappings(mapping1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.removeMappings(mapping2);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.removeMappings(mapping3);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // reset
                dragAndDropMappings.forEach(changedSubmittedAnswer::addMappings);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isTrue();

            }
            else if (submittedAnswer2 instanceof ShortAnswerSubmittedAnswer changedSubmittedAnswer) {
                var shortAnswerMappings = changedSubmittedAnswer.getSubmittedTexts().stream().toList();
                assertThat(shortAnswerMappings.size()).isEqualTo(2);
                ShortAnswerSubmittedText mapping1 = shortAnswerMappings.get(0);
                ShortAnswerSubmittedText mapping2 = shortAnswerMappings.get(1);

                // remove the first text
                changedSubmittedAnswer.removeSubmittedTexts(mapping1);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                changedSubmittedAnswer.removeSubmittedTexts(mapping2);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isFalse();

                // reset
                shortAnswerMappings.forEach(changedSubmittedAnswer::addSubmittedTexts);
                assertThat(isContentEqualTo(submittedAnswer1, changedSubmittedAnswer)).isTrue();
            }

        }
    }

    @Test
    void simpleCompareDnDMapping() {
        DnDMapping m1 = new DnDMapping(1, 2);
        DnDMapping m2 = new DnDMapping(3, 4);
        DnDMapping m3 = new DnDMapping(4, 3); // item id and location id of m2 switched
        DnDMapping m4 = new DnDMapping(1, 2); // same as m1
        DnDMapping m5 = new DnDMapping(3, 4);  // same as m2
        DnDMapping m6 = new DnDMapping(4, 3);  // same as m3

        Set<DnDMapping> set1 = Set.of(m1, m2);
        Set<DnDMapping> set2 = Set.of(m2, m1);  // same as set1
        Set<DnDMapping> set3 = Set.of(m1, m3);
        Set<DnDMapping> set4 = Set.of(m4, m2); // same as set1
        Set<DnDMapping> set5 = Set.of(m5, m4); // same as set1
        Set<DnDMapping> set6 = Set.of(m1, m2, m3);
        Set<DnDMapping> set7 = Set.of(m3, m4, m5);  // same as set6
        Set<DnDMapping> set8 = Set.of(m1, m6);  // same as set3

        assertThat(Objects.equals(set1, set2)).isTrue();
        assertThat(Objects.equals(set1, set3)).isFalse();
        assertThat(Objects.equals(set1, set4)).isTrue();
        assertThat(Objects.equals(set1, set5)).isTrue();
        assertThat(Objects.equals(set1, set6)).isFalse();
        assertThat(Objects.equals(set1, set7)).isFalse();
        assertThat(Objects.equals(set1, set8)).isFalse();

        assertThat(Objects.equals(set2, set3)).isFalse();
        assertThat(Objects.equals(set2, set4)).isTrue();
        assertThat(Objects.equals(set2, set5)).isTrue();
        assertThat(Objects.equals(set2, set6)).isFalse();
        assertThat(Objects.equals(set2, set7)).isFalse();
        assertThat(Objects.equals(set2, set8)).isFalse();

        assertThat(Objects.equals(set3, set4)).isFalse();
        assertThat(Objects.equals(set3, set5)).isFalse();
        assertThat(Objects.equals(set3, set6)).isFalse();
        assertThat(Objects.equals(set3, set7)).isFalse();
        assertThat(Objects.equals(set3, set8)).isTrue();

        assertThat(Objects.equals(set4, set5)).isTrue();  // both same as set1
        assertThat(Objects.equals(set4, set6)).isFalse();
        assertThat(Objects.equals(set4, set7)).isFalse();
        assertThat(Objects.equals(set4, set8)).isFalse();

        assertThat(Objects.equals(set5, set6)).isFalse();
        assertThat(Objects.equals(set5, set7)).isFalse();
        assertThat(Objects.equals(set5, set8)).isFalse();

        assertThat(Objects.equals(set6, set7)).isTrue();
        assertThat(Objects.equals(set6, set8)).isFalse();

        assertThat(Objects.equals(set7, set8)).isFalse();
    }

    @Test
    void simpleCompareSAMapping() {
        SAMapping m1 = new SAMapping(1, "2");
        SAMapping m2 = new SAMapping(3, "4");
        SAMapping m3 = new SAMapping(6, "5"); // item id and location id of m2 switched
        SAMapping m4 = new SAMapping(1, "2"); // same as m1
        SAMapping m5 = new SAMapping(3, "4"); // same as m2
        SAMapping m6 = new SAMapping(3, null);
        SAMapping m7 = new SAMapping(3, null);

        Set<SAMapping> set1 = Set.of(m1, m2);
        Set<SAMapping> set2 = Set.of(m2, m1);  // same as set1
        Set<SAMapping> set3 = Set.of(m1, m3);
        Set<SAMapping> set4 = Set.of(m4, m2);  // same as set1
        Set<SAMapping> set5 = Set.of(m5, m4);  // same as set1
        Set<SAMapping> set6 = Set.of(m1, m5, m3);
        Set<SAMapping> set7 = Set.of(m3, m4, m2); // same as set6
        Set<SAMapping> set8 = Set.of(m2, m6);
        Set<SAMapping> set9 = Set.of(m5, m7); // same as set8

        assertThat(Objects.equals(set1, set2)).isTrue();
        assertThat(Objects.equals(set1, set3)).isFalse();
        assertThat(Objects.equals(set1, set4)).isTrue();
        assertThat(Objects.equals(set1, set5)).isTrue();
        assertThat(Objects.equals(set1, set6)).isFalse();
        assertThat(Objects.equals(set1, set7)).isFalse();
        assertThat(Objects.equals(set1, set8)).isFalse();
        assertThat(Objects.equals(set1, set9)).isFalse();

        assertThat(Objects.equals(set2, set3)).isFalse();
        assertThat(Objects.equals(set2, set4)).isTrue();
        assertThat(Objects.equals(set2, set5)).isTrue();
        assertThat(Objects.equals(set2, set6)).isFalse();
        assertThat(Objects.equals(set2, set7)).isFalse();
        assertThat(Objects.equals(set2, set8)).isFalse();
        assertThat(Objects.equals(set2, set9)).isFalse();

        assertThat(Objects.equals(set3, set4)).isFalse(); // different from all other sets
        assertThat(Objects.equals(set3, set5)).isFalse();
        assertThat(Objects.equals(set3, set6)).isFalse();
        assertThat(Objects.equals(set3, set7)).isFalse();
        assertThat(Objects.equals(set3, set8)).isFalse();
        assertThat(Objects.equals(set3, set9)).isFalse();

        assertThat(Objects.equals(set4, set5)).isTrue(); // both same as set1
        assertThat(Objects.equals(set4, set6)).isFalse();
        assertThat(Objects.equals(set4, set7)).isFalse();
        assertThat(Objects.equals(set4, set8)).isFalse();
        assertThat(Objects.equals(set4, set9)).isFalse();

        assertThat(Objects.equals(set5, set6)).isFalse();
        assertThat(Objects.equals(set5, set7)).isFalse();
        assertThat(Objects.equals(set5, set8)).isFalse();
        assertThat(Objects.equals(set5, set9)).isFalse();

        assertThat(Objects.equals(set6, set7)).isTrue();
        assertThat(Objects.equals(set4, set8)).isFalse();
        assertThat(Objects.equals(set4, set9)).isFalse();

        assertThat(Objects.equals(set8, set9)).isTrue();
    }

    private List<QuizQuestion> setAllQuizQuestionIds(QuizExercise quizExercise) {
        List<QuizQuestion> questions = quizExercise.getQuizQuestions();

        Long id = 1L;
        for (var question : questions) {
            id = setQuizQuestionIds(question, id);
        }

        return questions;
    }

    private Long setQuizQuestionIds(QuizQuestion question, Long id) {
        question.setId(id);
        id++;
        if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
            for (var item : dragAndDropQuestion.getDragItems()) {
                item.setId(id);
                id++;
            }

            for (var location : dragAndDropQuestion.getDropLocations()) {
                location.setId(id);
                id++;
            }

        }
        else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
            for (var spot : shortAnswerQuestion.getSpots()) {
                spot.setId(id);
                id++;
            }

        }
        else if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
            for (var answerOption : multipleChoiceQuestion.getAnswerOptions()) {
                answerOption.setId(id);
                id++;
            }
        }
        return id;
    }
}
