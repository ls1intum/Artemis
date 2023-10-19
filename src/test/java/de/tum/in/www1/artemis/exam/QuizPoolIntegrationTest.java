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

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
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
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.service.QuizPoolService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

class QuizPoolIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "quizpoolintegration";

    @Autowired
    private QuizPoolService quizPoolService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

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
        assertThat(responseQuizPool.getQuizGroups()).hasSize(quizPool.getQuizGroups().size()).extracting("name").containsExactly(quizPool.getQuizGroups().get(0).getName(),
                quizPool.getQuizGroups().get(1).getName(), quizPool.getQuizGroups().get(2).getName());
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(quizPool.getQuizQuestions().size()).extracting("title", "quizGroup.name").containsExactly(
                tuple(quizPool.getQuizQuestions().get(0).getTitle(), quizGroup0.getName()), tuple(quizPool.getQuizQuestions().get(1).getTitle(), quizGroup0.getName()),
                tuple(quizPool.getQuizQuestions().get(2).getTitle(), quizGroup1.getName()), tuple(quizPool.getQuizQuestions().get(3).getTitle(), quizGroup2.getName()),
                tuple(quizPool.getQuizQuestions().get(4).getTitle(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolSuccessful() throws Exception {
        QuizPool quizPool = createQuizPool();

        QuizGroup quizGroup3 = quizExerciseUtilService.createQuizGroup("Exception Handling");
        QuizQuestion saQuizQuestion1 = quizExerciseUtilService.createShortAnswerQuestionWithTitleAndGroup("SA 1", quizGroup2);
        QuizQuestion saQuizQuestion2 = quizExerciseUtilService.createShortAnswerQuestionWithTitleAndGroup("SA 2", quizGroup3);
        QuizQuestion saQuizQuestion3 = quizExerciseUtilService.createShortAnswerQuestionWithTitleAndGroup("SA 3", null);
        quizPool.setQuizGroups(List.of(quizPool.getQuizGroups().get(0), quizPool.getQuizGroups().get(2), quizGroup3));
        quizPool.setQuizQuestions(List.of(quizPool.getQuizQuestions().get(0), quizPool.getQuizQuestions().get(1), quizPool.getQuizQuestions().get(2), saQuizQuestion1,
                saQuizQuestion2, saQuizQuestion3));
        quizPool.getQuizQuestions().get(2).setQuizGroup(quizGroup3);

        QuizPool responseQuizPool = request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class,
                HttpStatus.OK, null);

        assertThat(responseQuizPool.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool.getQuizGroups()).hasSize(quizPool.getQuizGroups().size()).extracting("name").containsExactly(quizPool.getQuizGroups().get(0).getName(),
                quizPool.getQuizGroups().get(1).getName(), quizPool.getQuizGroups().get(2).getName());
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(quizPool.getQuizQuestions().size()).extracting("title", "quizGroup.name").containsExactly(
                tuple(quizPool.getQuizQuestions().get(0).getTitle(), quizGroup0.getName()), tuple(quizPool.getQuizQuestions().get(1).getTitle(), quizGroup0.getName()),
                tuple(quizPool.getQuizQuestions().get(2).getTitle(), quizGroup3.getName()), tuple(quizPool.getQuizQuestions().get(3).getTitle(), quizGroup2.getName()),
                tuple(quizPool.getQuizQuestions().get(4).getTitle(), quizGroup3.getName()), tuple(quizPool.getQuizQuestions().get(5).getTitle(), null));
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
        QuizGroup quizGroup0 = quizExerciseUtilService.createQuizGroup("Encapsulation");
        QuizGroup quizGroup1 = quizExerciseUtilService.createQuizGroup("Inheritance");
        QuizQuestion mcQuizQuestion = quizExerciseUtilService.createMultipleChoiceQuestionWithTitleAndGroup("MC", quizGroup0);
        QuizQuestion dndQuizQuestion = quizExerciseUtilService.createDragAndDropQuestionWithTitleAndGroup("DND", quizGroup1);
        QuizQuestion saQuizQuestion = quizExerciseUtilService.createShortAnswerQuestionWithTitleAndGroup("SA", null);
        quizPool.setQuizGroups(List.of(quizGroup0, quizGroup1));
        quizPool.setQuizQuestions(List.of(mcQuizQuestion, dndQuizQuestion, saQuizQuestion));
        QuizPool savedQuizPool = quizPoolService.update(exam.getId(), quizPool);

        QuizPool responseQuizPool = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", HttpStatus.OK, QuizPool.class);
        assertThat(responseQuizPool.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool.getQuizGroups()).hasSize(savedQuizPool.getQuizGroups().size()).containsExactly(savedQuizPool.getQuizGroups().get(0),
                savedQuizPool.getQuizGroups().get(1));
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(savedQuizPool.getQuizQuestions().size()).containsExactly(savedQuizPool.getQuizQuestions().get(0),
                savedQuizPool.getQuizQuestions().get(1), savedQuizPool.getQuizQuestions().get(2));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQuizPoolNotFoundExam() throws Exception {
        int notFoundExamId = 0;
        request.get("/api/courses/" + course.getId() + "/exams/" + notFoundExamId + "/quiz-pools", HttpStatus.NOT_FOUND, QuizPool.class);
    }

    private QuizPool createQuizPool() throws Exception {
        quizGroup0 = quizExerciseUtilService.createQuizGroup("Encapsulation");
        quizGroup1 = quizExerciseUtilService.createQuizGroup("Inheritance");
        quizGroup2 = quizExerciseUtilService.createQuizGroup("Polymorphism");
        QuizQuestion mcQuizQuestion0 = quizExerciseUtilService.createMultipleChoiceQuestionWithTitleAndGroup("MC 0", quizGroup0);
        QuizQuestion mcQuizQuestion1 = quizExerciseUtilService.createMultipleChoiceQuestionWithTitleAndGroup("MC 1", quizGroup0);
        QuizQuestion dndQuizQuestion0 = quizExerciseUtilService.createDragAndDropQuestionWithTitleAndGroup("DND 0", quizGroup1);
        QuizQuestion dndQuizQuestion1 = quizExerciseUtilService.createDragAndDropQuestionWithTitleAndGroup("DND 1", quizGroup2);
        QuizQuestion saQuizQuestion0 = quizExerciseUtilService.createShortAnswerQuestionWithTitleAndGroup("SA 0", null);
        quizPool.setQuizGroups(List.of(quizGroup0, quizGroup1, quizGroup2));
        quizPool.setQuizQuestions(List.of(mcQuizQuestion0, mcQuizQuestion1, dndQuizQuestion0, dndQuizQuestion1, saQuizQuestion0));

        return request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.OK, null);
    }
}
