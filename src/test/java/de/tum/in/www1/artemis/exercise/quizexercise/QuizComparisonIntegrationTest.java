package de.tum.in.www1.artemis.exercise.quizexercise;

import static org.assertj.core.api.Assertions.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.service.exam.StudentExamService;

class QuizComparisonIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Test
    void testCompareQuizMultipleChoiceSubmissions() {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), QuizMode.INDIVIDUAL);
        QuizQuestion multipleChoice = quizExercise.getQuizQuestions().get(0);
        setQuizQuestionIds(multipleChoice, 1L);

        QuizSubmission quizSubmission1 = ParticipationFactory.generateQuizSubmission(true);
        QuizSubmission quizSubmission2 = ParticipationFactory.generateQuizSubmission(true);
        QuizSubmission quizSubmission3 = ParticipationFactory.generateQuizSubmission(true);

        quizSubmission1.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerFor(multipleChoice, true));
        quizSubmission2.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerFor(multipleChoice, true));
        quizSubmission3.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerFor(multipleChoice, false));

        assertThat(studentExamService.isContentEqualTo(quizSubmission1, quizSubmission2)).isTrue();
        assertThat(studentExamService.isContentEqualTo(quizSubmission1, quizSubmission3)).isFalse();

    }

    @Test
    void testCompareEmptyQuizSubmissions() {
        QuizSubmission quizSubmission1 = ParticipationFactory.generateQuizSubmission(true);

        assertThat(studentExamService.isContentEqualTo((QuizSubmission) null, null)).isTrue();
        assertThat(studentExamService.isContentEqualTo(quizSubmission1, null)).isFalse();
    }

    // TODO.... more tests

    Long setQuizQuestionIds(QuizQuestion question, Long id) {
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
