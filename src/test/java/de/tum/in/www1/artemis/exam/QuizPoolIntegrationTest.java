package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestion;
import de.tum.in.www1.artemis.domain.quiz.QuizGroup;
import de.tum.in.www1.artemis.domain.quiz.QuizPool;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestion;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.service.QuizPoolService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

class QuizPoolIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "quizpoolintegration";

    @Autowired
    private QuizPoolService quizPoolService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private RequestUtilService request;

    private Course course;

    private Exam exam;

    private QuizPool quizPool;

    private QuizGroup quizGroup0;

    private QuizGroup quizGroup1;

    private QuizGroup quizGroup2;

    private MultipleChoiceQuestion mcQuizQuestion0;

    private MultipleChoiceQuestion mcQuizQuestion1;

    private DragAndDropQuestion dndQuizQuestion0;

    private DragAndDropQuestion dndQuizQuestion1;

    private ShortAnswerQuestion saQuizQuestion0;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.addEmptyCourse();
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.setGroups(Set.of(course.getInstructorGroupName()));
        exam = examUtilService.addExam(course);
        quizPool = quizPoolService.update(exam.getId(), new QuizPool());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuizPoolSuccessful() throws Exception {
        QuizPool responseQuizPool = createQuizPool();
        assertThat(responseQuizPool.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool.getQuizGroups()).hasSize(quizPool.getQuizGroups().size()).extracting("name")
                .containsExactlyInAnyOrder(quizPool.getQuizGroups().get(0).getName(), quizPool.getQuizGroups().get(1).getName(), quizPool.getQuizGroups().get(2).getName());
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(quizPool.getQuizQuestions().size()).extracting("title", "quizGroup.name").containsExactlyInAnyOrder(
                tuple(mcQuizQuestion0.getTitle(), quizGroup0.getName()), tuple(mcQuizQuestion1.getTitle(), quizGroup0.getName()),
                tuple(dndQuizQuestion0.getTitle(), quizGroup1.getName()), tuple(dndQuizQuestion1.getTitle(), quizGroup2.getName()), tuple(saQuizQuestion0.getTitle(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolSuccessful() throws Exception {
        QuizPool quizPool = createQuizPool();

        QuizGroup quizGroup3 = QuizExerciseFactory.createQuizGroup("Exception Handling");

        QuizQuestion saQuizQuestion1 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 1", quizGroup2);
        QuizQuestion saQuizQuestion2 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 2", quizGroup3);
        QuizQuestion saQuizQuestion3 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 3", null);

        quizPool.setQuizGroups(List.of(quizGroup0, quizGroup2, quizGroup3));

        quizPool.setQuizQuestions(List.of(mcQuizQuestion0, mcQuizQuestion1, dndQuizQuestion0, saQuizQuestion1, saQuizQuestion2, saQuizQuestion3));
        dndQuizQuestion0.setQuizGroup(quizGroup3);

        QuizPool responseQuizPool = request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class,
                HttpStatus.OK, null);

        assertThat(responseQuizPool.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool.getQuizGroups()).hasSize(quizPool.getQuizGroups().size()).extracting("name").containsExactlyInAnyOrder(quizGroup0.getName(),
                quizGroup2.getName(), quizGroup3.getName());
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(quizPool.getQuizQuestions().size()).extracting("title", "quizGroup.name").containsExactlyInAnyOrder(
                tuple(mcQuizQuestion0.getTitle(), quizGroup0.getName()), tuple(mcQuizQuestion1.getTitle(), quizGroup0.getName()),
                tuple(dndQuizQuestion0.getTitle(), quizGroup3.getName()), tuple(saQuizQuestion1.getTitle(), quizGroup2.getName()),
                tuple(saQuizQuestion2.getTitle(), quizGroup3.getName()), tuple(saQuizQuestion3.getTitle(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidMCQuestion() throws Exception {
        MultipleChoiceQuestion quizQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        quizQuestion.setTitle(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidDnDQuestion() throws Exception {
        DragAndDropQuestion quizQuestion = QuizExerciseFactory.createDragAndDropQuestion();
        quizQuestion.setCorrectMappings(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidSAQuestion() throws Exception {
        ShortAnswerQuestion quizQuestion = QuizExerciseFactory.createShortAnswerQuestion();
        quizQuestion.setCorrectMappings(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolNotFoundCourse() throws Exception {
        QuizQuestion quizQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        quizPool.setQuizQuestions(List.of(quizQuestion));

        int notFoundCourseId = 0;
        request.putWithResponseBody("/api/courses/" + notFoundCourseId + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.NOT_FOUND, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolNotFoundExam() throws Exception {
        QuizQuestion quizQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        quizPool.setQuizQuestions(List.of(quizQuestion));

        int notFoundExamId = 0;
        request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + notFoundExamId + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.NOT_FOUND, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQuizPoolSuccessful() throws Exception {
        QuizGroup quizGroup0 = QuizExerciseFactory.createQuizGroup("Encapsulation");
        QuizGroup quizGroup1 = QuizExerciseFactory.createQuizGroup("Inheritance");
        QuizQuestion mcQuizQuestion = QuizExerciseFactory.createMultipleChoiceQuestionWithTitleAndGroup("MC", quizGroup0);
        QuizQuestion dndQuizQuestion = QuizExerciseFactory.createDragAndDropQuestionWithTitleAndGroup("DND", quizGroup1);
        QuizQuestion saQuizQuestion = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA", null);
        quizPool.setQuizGroups(List.of(quizGroup0, quizGroup1));
        quizPool.setQuizQuestions(List.of(mcQuizQuestion, dndQuizQuestion, saQuizQuestion));
        QuizPool savedQuizPool = quizPoolService.update(exam.getId(), quizPool);

        QuizPool responseQuizPool = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", HttpStatus.OK, QuizPool.class);
        assertThat(responseQuizPool.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool.getQuizGroups()).hasSize(savedQuizPool.getQuizGroups().size()).containsExactlyInAnyOrder(savedQuizPool.getQuizGroups().get(0),
                savedQuizPool.getQuizGroups().get(1));
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(savedQuizPool.getQuizQuestions().size()).containsExactlyInAnyOrder(savedQuizPool.getQuizQuestions().get(0),
                savedQuizPool.getQuizQuestions().get(1), savedQuizPool.getQuizQuestions().get(2));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQuizPoolNotFound() throws Exception {
        Exam examWithoutQuizPool = examUtilService.addExam(course);
        QuizPool responseQuizPool = request.get("/api/courses/" + course.getId() + "/exams/" + examWithoutQuizPool.getId() + "/quiz-pools", HttpStatus.OK, QuizPool.class);
        assertThat(responseQuizPool).isNull();
    }

    private QuizPool createQuizPool() throws Exception {
        quizGroup0 = QuizExerciseFactory.createQuizGroup("Encapsulation");
        quizGroup1 = QuizExerciseFactory.createQuizGroup("Inheritance");
        quizGroup2 = QuizExerciseFactory.createQuizGroup("Polymorphism");
        mcQuizQuestion0 = QuizExerciseFactory.createMultipleChoiceQuestionWithTitleAndGroup("MC 0", quizGroup0);
        mcQuizQuestion1 = QuizExerciseFactory.createMultipleChoiceQuestionWithTitleAndGroup("MC 1", quizGroup0);
        dndQuizQuestion0 = QuizExerciseFactory.createDragAndDropQuestionWithTitleAndGroup("DND 0", quizGroup1);
        dndQuizQuestion1 = QuizExerciseFactory.createDragAndDropQuestionWithTitleAndGroup("DND 1", quizGroup2);
        saQuizQuestion0 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 0", null);
        quizPool.setQuizGroups(List.of(quizGroup0, quizGroup1, quizGroup2));
        quizPool.setQuizQuestions(List.of(mcQuizQuestion0, mcQuizQuestion1, dndQuizQuestion0, dndQuizQuestion1, saQuizQuestion0));

        return request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.OK, null);
    }
}
