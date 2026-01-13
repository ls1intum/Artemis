package de.tum.cit.aet.artemis.quiz;

import static de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService.zonedDateTimeBiPredicate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizAction;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

/**
 * Integration tests for exercise versioning on QuizExercise operations.
 */
class QuizExerciseVersionIntegrationTest extends AbstractQuizExerciseIntegrationTest {

    private static final String TEST_PREFIX = "quizexerciseversion";

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionUtilService exerciseVersionUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuizExercise_createsExerciseVersion() throws Exception {
        QuizExercise createdExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        assertThat(createdExercise).isNotNull();

        exerciseVersionUtilService.verifyExerciseVersionCreated(createdExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.QUIZ);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuizExerciseForExam_createsExerciseVersion() throws Exception {
        QuizExercise createdExercise = createQuizOnServerForExam();

        assertThat(createdExercise).isNotNull();

        exerciseVersionUtilService.verifyExerciseVersionCreated(createdExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.QUIZ);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportQuizExercise_createsExerciseVersion() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.handleDndQuizFileCreation(quizExercise,
                List.of(new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage2.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()),
                        new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage4.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes())));
        quizExerciseService.save(quizExercise);

        QuizExercise changedQuiz = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(changedQuiz).isNotNull();
        changedQuiz.setTitle("New title");

        QuizExercise createdExercise = importQuizExerciseWithFiles(changedQuiz, List.of(), HttpStatus.CREATED);
        assertThat(createdExercise).isNotNull();

        exerciseVersionUtilService.verifyExerciseVersionCreated(createdExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.QUIZ);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExercise_createsExerciseVersion() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setQuizQuestions(quizExercise.getQuizQuestions().stream().filter(question -> !(question instanceof DragAndDropQuestion)).toList());
        quizExercise.setDuration(3600);
        quizExercise = createQuizExerciseWithFiles(quizExercise, HttpStatus.CREATED, false);

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(quizExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.QUIZ);

        ExerciseVersionUtilService.updateExercise(quizExercise);
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4d).text("Q1");

        question.setScoringType(ScoringType.ALL_OR_NOTHING);
        question.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        question.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        question.setExplanation("Explanation");
        question.copyQuestionId();

        quizExercise.addQuestions(question);
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.setGradingInstructions(null);
        QuizExercise updatedExercise = updateQuizExerciseWithFiles(quizExercise, null, OK);
        assertThat(updatedExercise).isNotNull();

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(updatedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.QUIZ);

        // Verify that a new version was created (different from the original)
        assertThat(originalVersion.getId()).isNotEqualTo(newVersion.getId());
        assertThat(originalVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(newVersion.getExerciseSnapshot());
    }

    @ParameterizedTest
    @EnumSource(value = QuizAction.class, names = { "START_NOW", "END_NOW", "SET_VISIBLE" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPerformActionForQuizExercise_createsExerciseVersion(QuizAction action) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        switch (action) {
            case START_NOW:
                quizExercise.setQuizMode(QuizMode.SYNCHRONIZED);
                break;
            case END_NOW:
                quizExercise.setQuizMode(QuizMode.INDIVIDUAL);
                break;
            case SET_VISIBLE:
                quizExercise.setReleaseDate(ZonedDateTime.now().plusHours(10));
                break;
            default:
                return;
        }
        quizExerciseTestRepository.save(quizExercise);
        exerciseVersionService.createExerciseVersion(quizExercise);

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(quizExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.QUIZ);

        ExerciseVersionUtilService.updateExercise(quizExercise);

        QuizExercise updatedExercise = request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/" + action.getValue(), quizExercise, QuizExercise.class,
                OK);
        assertThat(updatedExercise).isNotNull();

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(updatedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.QUIZ);

        // Verify that a new version was created (different from the original)
        assertThat(originalVersion.getId()).isNotEqualTo(newVersion.getId());
        assertThat(originalVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(newVersion.getExerciseSnapshot());
    }

}
