package de.tum.cit.aet.artemis.hyperion.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class QuizGenerationResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private CourseTestRepository courseRepository;

    private static final String TEST_PREFIX = "quizgeneration";

    private long persistedCourseId;

    @BeforeEach
    void setupTestData() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        Course course = new Course();
        course.setTitle("Quiz Generation Test Course");
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

    private void mockSuccessfulQuizGeneration() {
        String validQuizResponse = """
                [
                  {
                    "title": "Test Question",
                    "text": "What is Java?",
                    "explanation": "Java is a programming language",
                    "hint": "Think about OOP",
                    "difficulty": 3,
                    "tags": [],
                    "subtype": "SINGLE_CORRECT",
                    "competencyIds": [],
                    "options": [
                      {"text": "A programming language", "correct": true, "feedback": "Correct!"},
                      {"text": "A coffee brand", "correct": false, "feedback": "Incorrect"},
                      {"text": "An island", "correct": false, "feedback": "Incorrect"}
                    ]
                  }
                ]
                """;
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(validQuizResponse))))).when(chatModel).call(any(Prompt.class));
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions").isArray());
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions").isArray());
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

        // Invalid: numberOfQuestions is 0 (must be at least 1)
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
}
