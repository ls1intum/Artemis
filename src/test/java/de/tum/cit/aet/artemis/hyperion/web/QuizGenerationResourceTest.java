package de.tum.cit.aet.artemis.hyperion.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@TestPropertySource(properties = "artemis.hyperion.enabled=true")
class QuizGenerationResourceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "quizgeneration";

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    private long courseId;

    @BeforeEach
    void setupTestData() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        Course course = courseUtilService.addEmptyCourse();
        course.setTitle("Quiz Generation Test Course");
        course.setShortName(TEST_PREFIX + "course");
        course.setStudentGroupName(TEST_PREFIX + "student");
        course.setTeachingAssistantGroupName(TEST_PREFIX + "tutor");
        course.setEditorGroupName(TEST_PREFIX + "editor");
        course.setInstructorGroupName(TEST_PREFIX + "instructor");
        course = courseRepository.save(course);
        courseId = course.getId();

        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var editor = userUtilService.getUserByLogin(TEST_PREFIX + "editor1");
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        student.getGroups().add(course.getStudentGroupName());
        tutor.getGroups().add(course.getTeachingAssistantGroupName());
        editor.getGroups().add(course.getEditorGroupName());
        instructor.getGroups().add(course.getInstructorGroupName());

        userTestRepository.save(student);
        userTestRepository.save(tutor);
        userTestRepository.save(editor);
        userTestRepository.save(instructor);
    }

    @AfterEach
    void cleanUpTestData() {
        courseRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldGenerateQuizForInstructor() throws Exception {
        String requestBody = """
                {
                  "topic": "Java Programming",
                  "numberOfQuestions": 1,
                  "language": "ENGLISH",
                  "difficultyLevel": "MEDIUM",
                  "requestedSubtype": "SINGLE_CORRECT",
                  "promptHint": "Focus on basics"
                }
                """;

        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldGenerateQuizForEditor() throws Exception {

        String requestBody = """
                {
                  "topic": "Java Programming",
                  "numberOfQuestions": 1,
                  "language": "ENGLISH",
                  "difficultyLevel": "MEDIUM",
                  "requestedSubtype": "SINGLE_CORRECT",
                  "promptHint": null
                }
                """;

        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForTutor() throws Exception {
        String body = """
                {
                  "topic": "Java Programming",
                  "numberOfQuestions": 1,
                  "language": "ENGLISH",
                  "difficultyLevel": "MEDIUM",
                  "requestedSubtype": "SINGLE_CORRECT",
                  "promptHint": null
                }
                """;

        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void shouldReturnForbiddenForStudent() throws Exception {
        String body = """
                {
                  "topic": "Java Programming",
                  "numberOfQuestions": 1,
                  "language": "ENGLISH",
                  "difficultyLevel": "MEDIUM",
                  "requestedSubtype": "SINGLE_CORRECT",
                  "promptHint": null
                }
                """;

        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForInvalidInput() throws Exception {
        String body = """
                {
                  "topic": "Java Programming",
                  "numberOfQuestions": 0,
                  "language": "ENGLISH",
                  "difficultyLevel": "MEDIUM",
                  "requestedSubtype": "SINGLE_CORRECT",
                  "promptHint": null
                }
                """;

        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnNotFoundForNonexistentCourse() throws Exception {
        long invalid = 9999L;

        String body = """
                {
                  "topic": "Java Programming",
                  "numberOfQuestions": 1,
                  "language": "ENGLISH",
                  "difficultyLevel": "MEDIUM",
                  "requestedSubtype": "SINGLE_CORRECT",
                  "promptHint": null
                }
                """;

        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", invalid).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
