package de.tum.cit.aet.artemis.hyperion.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
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

class HyperionQuizQuestionGenerationResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private CourseTestRepository courseRepository;

    private static final String TEST_PREFIX = "hyperionquizgen";

    private long persistedCourseId;

    @BeforeEach
    void setupTestData() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        Course course = new Course();
        course.setTitle("Hyperion Test Course");
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
    void resetMocks() {
        reset(azureOpenAiChatModel);
    }

    private void mockGenerationSuccess() {
        String response = """
                {
                  "questions": [
                    {
                      "type": "single-choice",
                      "title": "REST Basics",
                      "questionText": "What does REST stand for?",
                      "options": [
                        {"text": "Representational State Transfer", "correct": true},
                        {"text": "Remote Execution Service Type", "correct": false}
                      ]
                    }
                  ]
                }
                """;
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(response))))).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldGenerateQuizQuestionsForInstructor() throws Exception {
        mockGenerationSuccess();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        String body = """
                {
                  "topic": "REST APIs",
                  "optionalPrompt": "Focus on fundamentals",
                  "language": "en",
                  "questionTypes": ["single-choice", "true-false"],
                  "numberOfQuestions": 3,
                  "difficulty": 45
                }
                """;

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/quiz-exercises/generate-questions", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions").isArray()).andExpect(jsonPath("$.questions[0].type").value("single-choice"))
                .andExpect(jsonPath("$.questions[0].title").value("REST Basics")).andExpect(jsonPath("$.questions[0].options").isArray());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldGenerateQuizQuestionsForEditor() throws Exception {
        mockGenerationSuccess();
        userUtilService.changeUser(TEST_PREFIX + "editor1");

        String body = """
                {
                  "topic": "REST APIs",
                  "language": "en",
                  "questionTypes": ["single-choice"],
                  "numberOfQuestions": 1,
                  "difficulty": 20
                }
                """;

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/quiz-exercises/generate-questions", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForTutor() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        String body = """
                {
                  "topic": "REST APIs",
                  "language": "en",
                  "questionTypes": ["single-choice"],
                  "numberOfQuestions": 1,
                  "difficulty": 20
                }
                """;

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/quiz-exercises/generate-questions", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForStudent() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "student1");

        String body = """
                {
                  "topic": "REST APIs",
                  "language": "en",
                  "questionTypes": ["single-choice"],
                  "numberOfQuestions": 1,
                  "difficulty": 20
                }
                """;

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/quiz-exercises/generate-questions", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private static final String REFINE_BODY = """
            {
              "question": {
                "type": "single-choice",
                "title": "REST Basics",
                "questionText": "What does REST stand for?",
                "options": [
                  {"text": "Representational State Transfer", "correct": true},
                  {"text": "Remote Execution Service Type", "correct": false}
                ]
              },
              "refinementPrompt": "Make the question harder"
            }
            """;

    private void mockRefinementSuccess() {
        String response = """
                {
                  "question": {
                    "type": "single-choice",
                    "title": "REST Constraints",
                    "questionText": "Which constraint is NOT part of the REST architectural style?",
                    "options": [
                      {"text": "Stateless", "correct": false},
                      {"text": "Persistent connections", "correct": true}
                    ]
                  },
                  "reasoning": "Changed focus from definition to constraints."
                }
                """;
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(response))))).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRefineQuizQuestionForInstructor() throws Exception {
        mockRefinementSuccess();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/quiz-exercises/refine-question", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(REFINE_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.question").exists()).andExpect(jsonPath("$.reasoning").isNotEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldRefineQuizQuestionForEditor() throws Exception {
        mockRefinementSuccess();
        userUtilService.changeUser(TEST_PREFIX + "editor1");

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/quiz-exercises/refine-question", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(REFINE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForTutorOnRefine() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/quiz-exercises/refine-question", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(REFINE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForStudentOnRefine() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "student1");

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/quiz-exercises/refine-question", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(REFINE_BODY))
                .andExpect(status().isForbidden());
    }
}
