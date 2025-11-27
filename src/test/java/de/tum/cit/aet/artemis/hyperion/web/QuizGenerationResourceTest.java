package de.tum.cit.aet.artemis.hyperion.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuestionSubtype;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.GeneratedMcQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.McOptionDTO;
import de.tum.cit.aet.artemis.hyperion.service.AiQuizGenerationService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class QuizGenerationResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private CourseTestRepository courseRepository;

    @MockitoBean
    private AiQuizGenerationService aiQuizGenerationService;

    private static final String TEST_PREFIX = "quizgeneration";

    private long persistedCourseId;

    @BeforeEach
    void setupTestData() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        Course course = new Course();
        course.setTitle("Quiz Generation Test Course");
        course.setShortName(TEST_PREFIX + "course");
        course.setStudentGroupName(TEST_PREFIX + "student");
        course.setTeachingAssistantGroupName(TEST_PREFIX + "tutor");
        course.setEditorGroupName(TEST_PREFIX + "editor");
        course.setInstructorGroupName(TEST_PREFIX + "instructor");
        course = courseRepository.save(course);
        persistedCourseId = course.getId();

        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student.getGroups().add(course.getStudentGroupName());
        userTestRepository.save(student);
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        tutor.getGroups().add(course.getTeachingAssistantGroupName());
        userTestRepository.save(tutor);
        var editor = userUtilService.getUserByLogin(TEST_PREFIX + "editor1");
        editor.getGroups().add(course.getEditorGroupName());
        userTestRepository.save(editor);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.getGroups().add(course.getInstructorGroupName());
        userTestRepository.save(instructor);
    }

    @AfterEach
    void cleanUpTestData() {
        courseRepository.deleteAll();
    }

    private void mockSuccessfulQuizGeneration() {
        var option1 = new McOptionDTO("A programming language", true, "Correct!");
        var option2 = new McOptionDTO("A coffee brand", false, "Incorrect");
        var option3 = new McOptionDTO("An island", false, "Incorrect");

        var question = new GeneratedMcQuestionDTO("Test Question", "What is Java?", "Java is a programming language", "Think about OOP", 3, Set.of("java", "oop"),
                AiQuestionSubtype.SINGLE_CORRECT, Set.of(), List.of(option1, option2, option3));

        var response = new AiQuizGenerationResponseDTO(List.of(question), List.of());

        doReturn(response).when(aiQuizGenerationService).generate(anyLong(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldGenerateQuizForInstructor() throws Exception {
        long courseId = persistedCourseId;
        mockSuccessfulQuizGeneration();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();

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
        long courseId = persistedCourseId;
        mockSuccessfulQuizGeneration();
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        courseRepository.findById(courseId).orElseThrow();

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
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        courseRepository.findById(courseId).orElseThrow();

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
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForStudent() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "student1");
        courseRepository.findById(courseId).orElseThrow();

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
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForInvalidInput() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();

        String requestBody = """
                {
                  "topic": "Java Programming",
                  "numberOfQuestions": 0,
                  "language": "ENGLISH",
                  "difficultyLevel": "MEDIUM",
                  "requestedSubtype": "SINGLE_CORRECT",
                  "promptHint": null
                }
                """;

        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnNotFoundForNonexistentCourse() throws Exception {
        long invalidCourseId = 9999L;
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
        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", invalidCourseId).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldReturnInternalServerErrorWhenServiceThrows() throws Exception {
        doThrow(new RuntimeException("AI generation failed")).when(aiQuizGenerationService).generate(anyLong(), any());

        String requestBody = """
                {
                  "topic": "Java",
                  "numberOfQuestions": 1,
                  "language": "ENGLISH",
                  "difficultyLevel": "MEDIUM",
                  "requestedSubtype": "SINGLE_CORRECT",
                  "promptHint": "Focus on basics"
                }
                """;

        request.performMvcRequest(post("/api/hyperion/quizzes/courses/{courseId}/generate", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isInternalServerError());
    }
}
