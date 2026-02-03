package de.tum.cit.aet.artemis.hyperion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

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
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class HyperionProblemStatementResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ExerciseVersionRepository exerciseVersionRepository;

    private static final String TEST_PREFIX = "hyperionproblemstatementresource";

    private long persistedExerciseId;

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
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.title").value("Failed to generate problem statement: AI service unavailable"))
                .andExpect(jsonPath("$.message").value("error.problemStatementGenerationFailed")).andExpect(jsonPath("$.errorKey").value("problemStatementGenerationFailed"));
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRefineProblemStatementTargetedForInstructor() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Refined problem statement generated successfully.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"problemStatementText\":\"Original problem statement\",\"instruction\":\"Make it better\",\"startLine\":1,\"endLine\":2}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
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
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.title").value("Failed to refine problem statement: AI service unavailable"))
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
    void shouldReturnBadRequestForInvalidTargetedRefinement() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();

        // 1. Invalid line range (start > end)
        String bodyInvalidLines = "{\"problemStatementText\":\"Text\",\"instruction\":\"Fix\",\"startLine\":5,\"endLine\":3}";
        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(bodyInvalidLines))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("startLine must be less than or equal to endLine")));

        // 2. Invalid columns (start > end on same line)
        String bodyInvalidCols = "{\"problemStatementText\":\"Text\",\"instruction\":\"Fix\",\"startLine\":5,\"endLine\":5,\"startColumn\":10,\"endColumn\":5}";
        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(bodyInvalidCols))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("startColumn must be less than or equal to endColumn")));

        // 3. Mismatched columns (one null, one present)
        String bodyOneCol = "{\"problemStatementText\":\"Text\",\"instruction\":\"Fix\",\"startLine\":5,\"endLine\":5,\"startColumn\":10}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(bodyOneCol))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("startColumn and endColumn must be either both null or both non-null")));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldCreateExerciseVersionWhenGeneratingWithExerciseId() throws Exception {
        long courseId = persistedCourseId;
        long exerciseId = persistedExerciseId;
        mockChatSuccess("Generated problem statement with versioning.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Get initial version timestamp
        Optional<ExerciseVersion> versionBefore = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        var timestampBefore = versionBefore.map(ExerciseVersion::getCreatedDate).orElse(null);

        // Generate problem statement with exerciseId
        String body = "{\"userPrompt\":\"Create a sorting algorithm exercise\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate?exerciseId={exerciseId}", courseId, exerciseId)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.draftProblemStatement").isString());

        // Verify new version was created
        Optional<ExerciseVersion> versionAfter = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        assertThat(versionAfter).isPresent();

        // Verify it's a new version (different timestamp or first version)
        if (timestampBefore != null) {
            assertThat(versionAfter.get().getCreatedDate()).isAfter(timestampBefore);
        }

        // Verify the new version has correct metadata
        assertThat(versionAfter.get().getExerciseId()).isEqualTo(exerciseId);
        assertThat(versionAfter.get().getAuthorId()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldNotCreateExerciseVersionWhenGeneratingWithoutExerciseId() throws Exception {
        long courseId = persistedCourseId;
        long exerciseId = persistedExerciseId;
        mockChatSuccess("Generated problem statement without versioning.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Get initial version timestamp
        Optional<ExerciseVersion> versionBefore = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        var timestampBefore = versionBefore.map(ExerciseVersion::getCreatedDate).orElse(null);

        // Generate problem statement WITHOUT exerciseId (simulating creation)
        String body = "{\"userPrompt\":\"Create a sorting algorithm exercise\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftProblemStatement").isString());

        // Verify NO new version was created (timestamp should be same or no version
        // exists)
        Optional<ExerciseVersion> versionAfter = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        if (timestampBefore != null) {
            assertThat(versionAfter).isPresent();
            assertThat(versionAfter.get().getCreatedDate()).isEqualTo(timestampBefore);
        }
        else {
            assertThat(versionAfter).isEmpty();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldCreateExerciseVersionWhenRefiningGloballyWithExerciseId() throws Exception {
        long courseId = persistedCourseId;
        long exerciseId = persistedExerciseId;
        mockChatSuccess("Refined problem statement with versioning.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Get initial version timestamp
        Optional<ExerciseVersion> versionBefore = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        var timestampBefore = versionBefore.map(ExerciseVersion::getCreatedDate).orElse(null);

        // Refine problem statement globally with exerciseId
        String body = "{\"problemStatementText\":\"Original problem statement\",\"userPrompt\":\"Make it more detailed\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/global?exerciseId={exerciseId}", courseId, exerciseId)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());

        // Verify new version was created
        Optional<ExerciseVersion> versionAfter = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        assertThat(versionAfter).isPresent();
        if (timestampBefore != null) {
            assertThat(versionAfter.get().getCreatedDate()).isAfter(timestampBefore);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldNotCreateExerciseVersionWhenRefiningGloballyWithoutExerciseId() throws Exception {
        long courseId = persistedCourseId;
        long exerciseId = persistedExerciseId;
        mockChatSuccess("Refined problem statement without versioning.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Get initial version timestamp
        Optional<ExerciseVersion> versionBefore = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        var timestampBefore = versionBefore.map(ExerciseVersion::getCreatedDate).orElse(null);

        // Refine problem statement globally WITHOUT exerciseId
        String body = "{\"problemStatementText\":\"Original problem statement\",\"userPrompt\":\"Make it more detailed\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/global", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());

        // Verify NO new version was created
        Optional<ExerciseVersion> versionAfter = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        if (timestampBefore != null) {
            assertThat(versionAfter).isPresent();
            assertThat(versionAfter.get().getCreatedDate()).isEqualTo(timestampBefore);
        }
        else {
            assertThat(versionAfter).isEmpty();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldCreateExerciseVersionWhenRefiningTargetedWithExerciseId() throws Exception {
        long courseId = persistedCourseId;
        long exerciseId = persistedExerciseId;
        mockChatSuccess("Refined problem statement with targeted instructions.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Get initial version timestamp
        Optional<ExerciseVersion> versionBefore = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        var timestampBefore = versionBefore.map(ExerciseVersion::getCreatedDate).orElse(null);

        // Refine problem statement with targeted instructions and exerciseId
        String body = "{\"problemStatementText\":\"Original problem statement\",\"instruction\":\"Add more examples\",\"startLine\":1,\"endLine\":2}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted?exerciseId={exerciseId}", courseId, exerciseId)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());

        // Verify new version was created
        Optional<ExerciseVersion> versionAfter = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        assertThat(versionAfter).isPresent();
        if (timestampBefore != null) {
            assertThat(versionAfter.get().getCreatedDate()).isAfter(timestampBefore);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldNotCreateExerciseVersionWhenRefiningTargetedWithoutExerciseId() throws Exception {
        long courseId = persistedCourseId;
        long exerciseId = persistedExerciseId;
        mockChatSuccess("Refined problem statement without versioning.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Get initial version timestamp
        Optional<ExerciseVersion> versionBefore = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        var timestampBefore = versionBefore.map(ExerciseVersion::getCreatedDate).orElse(null);

        // Refine problem statement with targeted instructions WITHOUT exerciseId
        String body = "{\"problemStatementText\":\"Original problem statement\",\"instruction\":\"Add more examples\",\"startLine\":1,\"endLine\":2}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/refine/targeted", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refinedProblemStatement").isString());

        // Verify NO new version was created
        Optional<ExerciseVersion> versionAfter = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        if (timestampBefore != null) {
            assertThat(versionAfter).isPresent();
            assertThat(versionAfter.get().getCreatedDate()).isEqualTo(timestampBefore);
        }
        else {
            assertThat(versionAfter).isEmpty();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestWhenExerciseDoesNotBelongToCourse() throws Exception {
        long courseId = persistedCourseId;
        mockChatSuccess("Generated problem statement.");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Create a second course and exercise
        Course otherCourse = new Course();
        otherCourse.setTitle("Other Course");
        otherCourse.setStudentGroupName(TEST_PREFIX + "student");
        otherCourse.setTeachingAssistantGroupName(TEST_PREFIX + "tutor");
        otherCourse.setEditorGroupName(TEST_PREFIX + "editor");
        otherCourse.setInstructorGroupName(TEST_PREFIX + "instructor");
        otherCourse = courseRepository.save(otherCourse);

        ProgrammingExercise otherExercise = new ProgrammingExercise();
        otherExercise.setTitle("Other Exercise");
        otherExercise.setCourse(otherCourse);
        otherExercise = programmingExerciseRepository.save(otherExercise);
        long otherExerciseId = otherExercise.getId();

        // Try to generate with exerciseId from different course
        String body = "{\"userPrompt\":\"Create exercise\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate?exerciseId={exerciseId}", courseId, otherExerciseId)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorKey").value("exerciseNotInCourse"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldCreateExerciseVersionForEditorWithExerciseId() throws Exception {
        long courseId = persistedCourseId;
        long exerciseId = persistedExerciseId;
        mockChatSuccess("Generated problem statement for editor.");
        userUtilService.changeUser(TEST_PREFIX + "editor1");

        // Get initial version timestamp
        Optional<ExerciseVersion> versionBefore = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        var timestampBefore = versionBefore.map(ExerciseVersion::getCreatedDate).orElse(null);

        // Generate problem statement with exerciseId as editor
        String body = "{\"userPrompt\":\"Create exercise\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate?exerciseId={exerciseId}", courseId, exerciseId)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        // Verify new version was created
        Optional<ExerciseVersion> versionAfter = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        assertThat(versionAfter).isPresent();
        if (timestampBefore != null) {
            assertThat(versionAfter.get().getCreatedDate()).isAfter(timestampBefore);
        }
        assertThat(versionAfter.get().getExerciseId()).isEqualTo(exerciseId);
    }
}
