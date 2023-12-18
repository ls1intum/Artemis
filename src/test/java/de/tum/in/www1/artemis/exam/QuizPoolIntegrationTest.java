package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
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
    private UserUtilService userUtilService;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ObjectMapper objectMapper;

    private Course course;

    private Exam exam;

    private QuizPool quizPool;

    private QuizGroup quizGroup0;

    private QuizGroup quizGroup1;

    private QuizGroup quizGroup2;

    @BeforeEach
    void initTestCase() throws IOException {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.addEmptyCourse();
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.setGroups(Set.of(course.getInstructorGroupName()));
        exam = examUtilService.addExam(course);
        quizPool = quizPoolService.update(exam.getId(), new QuizPool(), null);
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

        QuizGroup quizGroup3 = QuizExerciseFactory.createQuizGroup("Exception Handling");
        QuizQuestion saQuizQuestion1 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 1", quizGroup2);
        QuizQuestion saQuizQuestion2 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 2", quizGroup3);
        QuizQuestion saQuizQuestion3 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 3", null);
        quizPool.setQuizGroups(List.of(quizPool.getQuizGroups().get(0), quizPool.getQuizGroups().get(2), quizGroup3));
        quizPool.setQuizQuestions(List.of(quizPool.getQuizQuestions().get(0), quizPool.getQuizQuestions().get(1), saQuizQuestion1, saQuizQuestion2, saQuizQuestion3));
        quizPool.getQuizQuestions().get(2).setQuizGroup(quizGroup3);

        MvcResult result = callQuizPoolUpdate(course.getId(), exam.getId(), quizPool, Collections.emptyList(), HttpStatus.OK);
        QuizPool responseQuizPool = objectMapper.readValue(result.getResponse().getContentAsString(), QuizPool.class);

        assertThat(responseQuizPool.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool.getQuizGroups()).hasSize(quizPool.getQuizGroups().size()).extracting("name").containsExactly(quizPool.getQuizGroups().get(0).getName(),
                quizPool.getQuizGroups().get(1).getName(), quizPool.getQuizGroups().get(2).getName());
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(quizPool.getQuizQuestions().size()).extracting("title", "quizGroup.name").containsExactly(
                tuple(quizPool.getQuizQuestions().get(0).getTitle(), quizGroup0.getName()), tuple(quizPool.getQuizQuestions().get(1).getTitle(), quizGroup0.getName()),
                tuple(quizPool.getQuizQuestions().get(2).getTitle(), quizGroup3.getName()), tuple(quizPool.getQuizQuestions().get(3).getTitle(), quizGroup3.getName()),
                tuple(quizPool.getQuizQuestions().get(4).getTitle(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidMCQuestion() throws Exception {
        MultipleChoiceQuestion quizQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        quizQuestion.setTitle(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        callQuizPoolUpdate(course.getId(), exam.getId(), quizPool, Collections.emptyList(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidDnDQuestion() throws Exception {
        DragAndDropQuestion quizQuestion = QuizExerciseFactory.createDragAndDropQuestion();
        quizQuestion.setCorrectMappings(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        callQuizPoolUpdate(course.getId(), exam.getId(), quizPool, Collections.emptyList(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolBadRequestInvalidSAQuestion() throws Exception {
        ShortAnswerQuestion quizQuestion = QuizExerciseFactory.createShortAnswerQuestion();
        quizQuestion.setCorrectMappings(null);
        quizPool.setQuizQuestions(List.of(quizQuestion));

        callQuizPoolUpdate(course.getId(), exam.getId(), quizPool, Collections.emptyList(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolNotFoundCourse() throws Exception {
        QuizQuestion quizQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        quizPool.setQuizQuestions(List.of(quizQuestion));

        Long notFoundCourseId = 0L;
        callQuizPoolUpdate(notFoundCourseId, exam.getId(), quizPool, Collections.emptyList(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizPoolNotFoundExam() throws Exception {
        QuizQuestion quizQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        quizPool.setQuizQuestions(List.of(quizQuestion));

        Long notFoundExamId = 0L;
        callQuizPoolUpdate(course.getId(), notFoundExamId, quizPool, Collections.emptyList(), HttpStatus.NOT_FOUND);
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
        var result = callQuizPoolUpdate(course.getId(), exam.getId(), quizPool, List.of("dragItemImage2.png", "dragItemImage4.png"), HttpStatus.OK);
        QuizPool savedQuizPool = objectMapper.readValue(result.getResponse().getContentAsString(), QuizPool.class);

        QuizPool responseQuizPool = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/quiz-pools", HttpStatus.OK, QuizPool.class);
        assertThat(responseQuizPool.getExam().getId()).isEqualTo(exam.getId());
        assertThat(responseQuizPool.getQuizGroups()).hasSize(savedQuizPool.getQuizGroups().size()).containsExactly(savedQuizPool.getQuizGroups().get(0),
                savedQuizPool.getQuizGroups().get(1));
        assertThat(responseQuizPool.getQuizQuestions()).hasSize(savedQuizPool.getQuizQuestions().size()).containsExactly(savedQuizPool.getQuizQuestions().get(0),
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
        QuizQuestion mcQuizQuestion0 = QuizExerciseFactory.createMultipleChoiceQuestionWithTitleAndGroup("MC 0", quizGroup0);
        QuizQuestion mcQuizQuestion1 = QuizExerciseFactory.createMultipleChoiceQuestionWithTitleAndGroup("MC 1", quizGroup0);
        QuizQuestion dndQuizQuestion0 = QuizExerciseFactory.createDragAndDropQuestionWithTitleAndGroup("DND 0", quizGroup1);
        QuizQuestion dndQuizQuestion1 = QuizExerciseFactory.createDragAndDropQuestionWithTitleAndGroup("DND 1", quizGroup2);
        QuizQuestion saQuizQuestion0 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 0", null);
        quizPool.setQuizGroups(List.of(quizGroup0, quizGroup1, quizGroup2));
        quizPool.setQuizQuestions(List.of(mcQuizQuestion0, mcQuizQuestion1, dndQuizQuestion0, dndQuizQuestion1, saQuizQuestion0));

        var result = callQuizPoolUpdate(course.getId(), exam.getId(), quizPool, List.of("dragItemImage2.png", "dragItemImage4.png"), HttpStatus.OK);
        return objectMapper.readValue(result.getResponse().getContentAsString(), QuizPool.class);
    }

    private MvcResult callQuizPoolUpdate(Long courseId, Long examId, QuizPool quizPool, List<String> fileNames, HttpStatus expectedStatus) throws Exception {
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/courses/" + courseId + "/exams/" + examId + "/quiz-pools");
        for (String fileName : fileNames) {
            builder.file(new MockMultipartFile("files", fileName, MediaType.IMAGE_PNG_VALUE, "test".getBytes()));
        }
        builder.file(new MockMultipartFile("quizPool", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(quizPool))).contentType(MediaType.MULTIPART_FORM_DATA);
        return request.getMvc().perform(builder).andExpect(status().is(expectedStatus.value())).andReturn();
    }
}
