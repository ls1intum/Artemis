package de.tum.cit.aet.artemis.hyperion.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.AiQuizGenerationService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class QuizGenerationResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "quizgeneration";

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @MockitoSpyBean
    private AiQuizGenerationService aiQuizGenerationService;

    private long courseId;

    @BeforeEach
    void setupTestData() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        Course course = courseUtilService.addEmptyCourse();
        course.setTitle("Quiz Generation Test Course");
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

    private void stubSuccessfulGeneration() {
        // We don’t care about actual questions here – just that the endpoint can return 200.
        var response = new AiQuizGenerationResponseDTO(List.of());
        doReturn(response).when(aiQuizGenerationService).generate(anyLong(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldGenerateQuizForInstructor() throws Exception {
        stubSuccessfulGeneration();

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
        stubSuccessfulGeneration();

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
        // Bean validation should reject this before calling the service
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
    void shouldReturnForbiddenForNonexistentCourse() throws Exception {
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
