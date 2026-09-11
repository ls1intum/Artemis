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

import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class HyperionAssessmentCriteriaGenerationResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hyperioncriteriagen";

    private static final String TEXT_REQUEST = """
            {
              "problemStatement": "Explain idempotency.",
              "maxPoints": 5,
              "bonusPoints": 0,
              "gradingInstructions": "Reward precise terminology.",
              "exampleSolution": "Repeating the operation has the same effect."
            }
            """;

    @Autowired
    private CourseTestRepository courseRepository;

    private long courseId;

    @BeforeEach
    void setupTestData() {
        userUtilService.addUsers(TEST_PREFIX, 0, 1, 1, 0);
        Course course = CourseFactory.generateMinimalCourse();
        course.setTitle("Assessment Criteria Test Course");
        course = courseRepository.save(course);
        courseId = course.getId();

        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        userUtilService.enrollUserInCourse(tutor, course, CourseRole.TEACHING_ASSISTANT);

        var editor = userUtilService.getUserByLogin(TEST_PREFIX + "editor1");
        userUtilService.enrollUserInCourse(editor, course, CourseRole.EDITOR);
    }

    @AfterEach
    void resetMocks() {
        reset(azureOpenAiChatModel);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldGenerateAssessmentCriteriaForCourseEditor() throws Exception {
        mockGenerationSuccess();
        userUtilService.changeUser(TEST_PREFIX + "editor1");

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/assessment-criteria/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(TEXT_REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.criteria[0].title").value("Correctness")).andExpect(jsonPath("$.criteria[0].bonus").value(false))
                .andExpect(jsonPath("$.criteria[0].structuredGradingInstructions[0].credits").value(5.0))
                .andExpect(jsonPath("$.criteria[0].structuredGradingInstructions[0].id").doesNotExist());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldRejectCourseTutor() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/assessment-criteria/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(TEXT_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldRejectNonPositiveMaximumPoints() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        String requestBody = TEXT_REQUEST.replace("\"maxPoints\": 5", "\"maxPoints\": 0");

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/assessment-criteria/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldRejectNegativeBonusPoints() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        String requestBody = TEXT_REQUEST.replace("\"bonusPoints\": 0", "\"bonusPoints\": -1");

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/assessment-criteria/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest());
    }

    private void mockGenerationSuccess() {
        String response = """
                {
                  "criteria": [{
                    "title": "Correctness",
                    "bonus": false,
                    "structuredGradingInstructions": [{
                      "credits": 5,
                      "gradingScale": "Full credit",
                      "instructionDescription": "The explanation is correct.",
                      "feedback": "Your explanation is correct.",
                      "usageCount": 1
                    }, {
                      "credits": 2.5,
                      "gradingScale": "Partial credit",
                      "instructionDescription": "The explanation is partly correct.",
                      "feedback": "Complete the explanation.",
                      "usageCount": 1
                    }, {
                      "credits": 0,
                      "gradingScale": "No credit",
                      "instructionDescription": "The explanation is incorrect.",
                      "feedback": "Review idempotency.",
                      "usageCount": 1
                    }]
                  }]
                }
                """;
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(response))))).when(azureOpenAiChatModel).call(any(Prompt.class));
    }
}
