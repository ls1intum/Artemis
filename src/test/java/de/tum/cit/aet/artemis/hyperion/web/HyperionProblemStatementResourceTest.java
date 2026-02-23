package de.tum.cit.aet.artemis.hyperion.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class HyperionProblemStatementResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private static final String TEST_PREFIX = "hyperionproblemstatementresource";

    private long persistedExerciseId;

    private long persistedCourseId;

    @AfterEach
    void resetMocks() {
        reset(azureOpenAiChatModel);
    }

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

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Hyperion Test Exercise");
        exercise.setCourse(course);
        exercise = programmingExerciseRepository.save(exercise);
        persistedExerciseId = exercise.getId();
    }

    private void mockConsistencyNoIssues() {
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"issues\":[]}"))))).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    private void mockRewriteImproved() {
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Improved problem statement."))))).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    private void mockChatSuccess(String responseMessage) {
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseMessage))))).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    private void mockChatFailure() {
        doThrow(new RuntimeException("AI service unavailable")).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnConsistencyIssuesEmptyForInstructor() throws Exception {
        long exerciseId = persistedExerciseId;
        mockConsistencyNoIssues();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        programmingExerciseRepository.findById(exerciseId).orElseThrow();
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldReturnConsistencyIssuesEmptyForEditor() throws Exception {
        long exerciseId = persistedExerciseId;
        mockConsistencyNoIssues();
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        programmingExerciseRepository.findById(exerciseId).orElseThrow();
        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForConsistencyCheckTutor() throws Exception {
        long exerciseId = persistedExerciseId;

        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        programmingExerciseRepository.findById(exerciseId).orElseThrow();

        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForConsistencyCheckStudent() throws Exception {
        long exerciseId = persistedExerciseId;

        userUtilService.changeUser(TEST_PREFIX + "student1");
        programmingExerciseRepository.findById(exerciseId).orElseThrow();

        request.performMvcRequest(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRewriteProblemStatementForInstructor() throws Exception {
        long courseId = persistedCourseId;
        mockRewriteImproved();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"problemStatementText\":\"Original\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rewrittenText").isString()).andExpect(jsonPath("$.improved").isBoolean());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldRewriteProblemStatementForEditor() throws Exception {
        long courseId = persistedCourseId;
        mockRewriteImproved();
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"problemStatementText\":\"Original\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rewrittenText").isString()).andExpect(jsonPath("$.improved").isBoolean());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForRewriteTutor() throws Exception {
        long courseId = persistedCourseId;

        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        courseRepository.findById(courseId).orElseThrow();

        String body = "{\"problemStatementText\":\"Original\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForRewriteStudent() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "student1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"problemStatementText\":\"Original\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldGenerateProblemStatementForInstructor() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Draft problem statement generated successfully.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"userPrompt\":\"Prompt\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftProblemStatement").isString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldGenerateProblemStatementForEditor() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Draft problem statement generated successfully.");
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"userPrompt\":\"Prompt\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftProblemStatement").isString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForGenerateTutor() throws Exception {
        long courseId = persistedCourseId;

        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        courseRepository.findById(courseId).orElseThrow();

        String body = "{\"userPrompt\":\"Prompt\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForGenerateStudent() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "student1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"userPrompt\":\"Prompt\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnInternalServerErrorWhenGenerationFails() throws Exception {
        long courseId = persistedCourseId;
        mockChatFailure();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"userPrompt\":\"Prompt\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.title").value("Failed to generate problem statement"))
                .andExpect(jsonPath("$.message").value("error.ProblemStatementGeneration.problemStatementGenerationFailed"))
                .andExpect(jsonPath("$.errorKey").value("ProblemStatementGeneration.problemStatementGenerationFailed"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRefineProblemStatementGloballyForInstructor() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Refined problem statement generated successfully.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"problemStatementText\":\"Original problem statement\",\"userPrompt\":\"Make it better\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/global", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldRefineProblemStatementForEditor() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Refined problem statement generated successfully.");
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"problemStatementText\":\"Original problem statement\",\"userPrompt\":\"Make it better\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/global", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForRefineTutor() throws Exception {
        long courseId = persistedCourseId;

        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        courseRepository.findById(courseId).orElseThrow();

        String body = "{\"problemStatementText\":\"Original problem statement\",\"userPrompt\":\"Make it better\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/global", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForRefineStudent() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "student1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"problemStatementText\":\"Original problem statement\",\"userPrompt\":\"Make it better\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/global", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnInternalServerErrorWhenRefinementFails() throws Exception {
        long courseId = persistedCourseId;
        mockChatFailure();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"problemStatementText\":\"Original problem statement\",\"userPrompt\":\"Make it better\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/global", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.title").value("Failed to refine problem statement"))
                .andExpect(jsonPath("$.message").value("error.ProblemStatementRefinement.problemStatementRefinementFailed"))
                .andExpect(jsonPath("$.errorKey").value("ProblemStatementRefinement.problemStatementRefinementFailed"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForEmptyRefinement() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        // Empty problem statement
        String body = "{\"problemStatementText\":\"\",\"userPrompt\":\"Make it better\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/global", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Method argument not valid")).andExpect(jsonPath("$.message").value("error.validation"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnForbiddenForGenerateWithNonExistentCourse() throws Exception {
        long nonExistentCourseId = 999999L;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        String body = "{\"userPrompt\":\"Prompt\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", nonExistentCourseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnForbiddenForRefineWithNonExistentCourse() throws Exception {
        long nonExistentCourseId = 999999L;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        String body = "{\"problemStatementText\":\"Original problem statement\",\"userPrompt\":\"Make it better\"}";
        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/problem-statements/refine/global", nonExistentCourseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnForbiddenForTargetedRefineWithNonExistentCourse() throws Exception {
        long nonExistentCourseId = 999999L;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        String body = "{\"problemStatementText\":\"Original\",\"startLine\":1,\"endLine\":1,\"instruction\":\"Fix this\"}";
        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", nonExistentCourseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForBlankGenerateUserPrompt() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"userPrompt\":\"   \"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForOverlongGenerateUserPrompt() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String longPrompt = "a".repeat(1001);
        String body = "{\"userPrompt\":\"" + longPrompt + "\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // Targeted refinement endpoint tests

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String buildTargetedRefinementBody(String problemStatement, int startLine, int endLine, Integer startColumn, Integer endColumn, String instruction) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("problemStatementText", problemStatement);
        node.put("startLine", startLine);
        node.put("endLine", endLine);
        if (startColumn != null) {
            node.put("startColumn", startColumn);
        }
        if (endColumn != null) {
            node.put("endColumn", endColumn);
        }
        node.put("instruction", instruction);
        return objectMapper.writeValueAsString(node);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRefineTargetedForInstructor() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Refined targeted problem statement.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = buildTargetedRefinementBody("Line one\nLine two\nLine three", 1, 2, null, null, "Improve clarity");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldRefineTargetedForEditor() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Refined targeted problem statement.");
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = buildTargetedRefinementBody("Line one\nLine two\nLine three", 1, 2, null, null, "Improve clarity");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForTargetedRefineTutor() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = buildTargetedRefinementBody("Some problem statement", 1, 1, null, null, "Fix this");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForTargetedRefineStudent() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "student1");
        courseRepository.findById(courseId).orElseThrow();
        String body = buildTargetedRefinementBody("Some problem statement", 1, 1, null, null, "Fix this");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForTargetedRefineStartLineGreaterThanEndLine() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        // startLine=5 > endLine=2 should be rejected
        String body = buildTargetedRefinementBody("Some problem statement text", 5, 2, null, null, "Improve this");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Bad Request")).andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForTargetedRefineEmptyInstruction() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = buildTargetedRefinementBody("Some problem statement text", 1, 1, null, null, "   ");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Bad Request")).andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForTargetedRefineEmptyProblemStatement() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = buildTargetedRefinementBody("", 1, 1, null, null, "Improve this");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Method argument not valid")).andExpect(jsonPath("$.message").value("error.validation"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForTargetedRefineInvalidColumnRange() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        // startColumn only, without endColumn — mismatched nullability
        String body = "{\"problemStatementText\":\"Some text\",\"startLine\":1,\"endLine\":1,\"startColumn\":3,\"instruction\":\"Fix this\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Bad Request")).andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForTargetedRefineSameLineStartColumnNotLessThanEndColumn() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        // same line, startColumn >= endColumn
        String body = buildTargetedRefinementBody("Some problem statement text", 1, 1, 5, 3, "Improve this");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Bad Request")).andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnInternalServerErrorWhenTargetedRefinementFails() throws Exception {
        long courseId = persistedCourseId;
        mockChatFailure();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = buildTargetedRefinementBody("Line one\nLine two\nLine three", 1, 2, null, null, "Improve clarity");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.title").value("Failed to refine problem statement"))
                .andExpect(jsonPath("$.message").value("error.ProblemStatementRefinement.problemStatementRefinementFailed"))
                .andExpect(jsonPath("$.errorKey").value("ProblemStatementRefinement.problemStatementRefinementFailed"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRefineTargetedWithColumnRange() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Refined with column range.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = buildTargetedRefinementBody("Some problem statement text", 1, 1, 1, 10, "Improve this part");
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());
    }
}
