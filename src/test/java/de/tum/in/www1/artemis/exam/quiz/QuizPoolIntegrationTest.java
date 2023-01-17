package de.tum.in.www1.artemis.exam.quiz;

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
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.quiz.QuizGroup;
import de.tum.in.www1.artemis.domain.exam.quiz.QuizPool;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestion;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestion;
import de.tum.in.www1.artemis.service.exam.QuizPoolService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class QuizPoolIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "quizpoolintegration";

    @Autowired
    private QuizPoolService quizPoolService;

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    private Course course;

    private Exam exam;

    private QuizPool quizPool;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = database.addEmptyCourse();
        User instructor = database.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.setGroups(Set.of(course.getInstructorGroupName()));
        exam = database.addExam(course);
        quizPool = exam.getQuizPool();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolSuccessful() throws Exception {
        QuizGroup quizGroup0 = new QuizGroup("Encapsulation");
        QuizGroup quizGroup1 = new QuizGroup("Inheritance");
        QuizGroup quizGroup2 = new QuizGroup("Polymorphism");
        QuizQuestion mcQuizQuestion0 = database.createMultipleChoiceQuestion();
        QuizQuestion mcQuizQuestion1 = database.createMultipleChoiceQuestion();
        QuizQuestion dndQuizQuestion0 = database.createDragAndDropQuestion();
        QuizQuestion dndQuizQuestion1 = database.createDragAndDropQuestion();
        QuizQuestion saQuizQuestion0 = database.createShortAnswerQuestion();
        mcQuizQuestion0.setTitle("MC 0");
        mcQuizQuestion1.setTitle("MC 1");
        dndQuizQuestion0.setTitle("DND 0");
        dndQuizQuestion1.setTitle("DND 1");
        saQuizQuestion0.setTitle("SA 0");
        mcQuizQuestion0.setQuizGroup(quizGroup0);
        mcQuizQuestion1.setQuizGroup(quizGroup0);
        dndQuizQuestion0.setQuizGroup(quizGroup1);
        dndQuizQuestion1.setQuizGroup(quizGroup2);
        quizPool.setQuizQuestions(List.of(mcQuizQuestion0, mcQuizQuestion1, dndQuizQuestion0, dndQuizQuestion1, saQuizQuestion0));

        QuizPool responseQuizPool = request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class,
                HttpStatus.OK, null);

        assertThat(responseQuizPool.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool.getQuizGroups()).hasSize(quizPool.getQuizGroups().size()).extracting("name").containsExactly(quizPool.getQuizGroups().get(0).getName(),
                quizPool.getQuizGroups().get(1).getName(), quizPool.getQuizGroups().get(2).getName());
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(quizPool.getQuizQuestions().size()).extracting("title", "quizGroup.name").containsExactly(
                tuple(quizPool.getQuizQuestions().get(0).getTitle(), quizGroup0.getName()), tuple(quizPool.getQuizQuestions().get(1).getTitle(), quizGroup0.getName()),
                tuple(quizPool.getQuizQuestions().get(2).getTitle(), quizGroup1.getName()), tuple(quizPool.getQuizQuestions().get(3).getTitle(), quizGroup2.getName()),
                tuple(quizPool.getQuizQuestions().get(4).getTitle(), null));

        QuizGroup quizGroup3 = new QuizGroup("Exception Handling");
        QuizQuestion saQuizQuestion1 = database.createShortAnswerQuestion();
        QuizQuestion saQuizQuestion2 = database.createShortAnswerQuestion();
        QuizQuestion saQuizQuestion3 = database.createShortAnswerQuestion();
        saQuizQuestion1.setTitle("SA 1");
        saQuizQuestion2.setTitle("SA 2");
        saQuizQuestion3.setTitle("SA 3");
        saQuizQuestion1.setQuizGroup(quizGroup2);
        saQuizQuestion2.setQuizGroup(quizGroup3);
        responseQuizPool.setQuizQuestions(List.of(responseQuizPool.getQuizQuestions().get(0), responseQuizPool.getQuizQuestions().get(1),
                responseQuizPool.getQuizQuestions().get(2), saQuizQuestion1, saQuizQuestion2, saQuizQuestion3));
        responseQuizPool.getQuizQuestions().get(2).setQuizGroup(quizGroup3);

        QuizPool responseQuizPool2 = request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", responseQuizPool, QuizPool.class,
                HttpStatus.OK, null);

        assertThat(responseQuizPool2.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool2.getQuizGroups()).hasSize(responseQuizPool.getQuizGroups().size()).extracting("name").containsExactly(
                responseQuizPool.getQuizGroups().get(0).getName(), responseQuizPool.getQuizGroups().get(1).getName(), responseQuizPool.getQuizGroups().get(2).getName());
        assertThat(responseQuizPool2.getQuizQuestions()).hasSize(responseQuizPool.getQuizQuestions().size()).extracting("title", "quizGroup.name").containsExactly(
                tuple(responseQuizPool.getQuizQuestions().get(0).getTitle(), quizGroup0.getName()),
                tuple(responseQuizPool.getQuizQuestions().get(1).getTitle(), quizGroup0.getName()),
                tuple(responseQuizPool.getQuizQuestions().get(2).getTitle(), quizGroup3.getName()),
                tuple(responseQuizPool.getQuizQuestions().get(3).getTitle(), quizGroup2.getName()),
                tuple(responseQuizPool.getQuizQuestions().get(4).getTitle(), quizGroup3.getName()), tuple(responseQuizPool.getQuizQuestions().get(5).getTitle(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidMCQuestion() throws Exception {
        MultipleChoiceQuestion quizQuestion = database.createMultipleChoiceQuestion();
        quizQuestion.setTitle(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidDnDQuestion() throws Exception {
        DragAndDropQuestion quizQuestion = database.createDragAndDropQuestion();
        quizQuestion.setCorrectMappings(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidSAQuestion() throws Exception {
        ShortAnswerQuestion quizQuestion = database.createShortAnswerQuestion();
        quizQuestion.setCorrectMappings(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolNotFoundCourse() throws Exception {
        QuizQuestion quizQuestion = database.createMultipleChoiceQuestion();
        quizQuestion.setTitle(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        int notFoundCourseId = 0;
        request.putWithResponseBody("/api/courses/" + notFoundCourseId + "/exams/" + exam.getId() + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.NOT_FOUND, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolNotFoundExam() throws Exception {
        QuizQuestion quizQuestion = database.createMultipleChoiceQuestion();
        quizQuestion.setTitle(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        int notFoundExamId = 0;
        request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + notFoundExamId + "/quiz-pools", quizPool, QuizPool.class, HttpStatus.NOT_FOUND, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQuizPoolSuccessful() throws Exception {
        QuizGroup quizGroup0 = new QuizGroup("Encapsulation");
        QuizGroup quizGroup1 = new QuizGroup("Inheritance");
        QuizQuestion mcQuizQuestion = database.createMultipleChoiceQuestion();
        QuizQuestion dndQuizQuestion = database.createDragAndDropQuestion();
        QuizQuestion saQuizQuestion = database.createShortAnswerQuestion();
        mcQuizQuestion.setTitle("MC");
        dndQuizQuestion.setTitle("DND");
        saQuizQuestion.setTitle("SA");
        mcQuizQuestion.setQuizGroup(quizGroup0);
        dndQuizQuestion.setQuizGroup(quizGroup1);
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
}
