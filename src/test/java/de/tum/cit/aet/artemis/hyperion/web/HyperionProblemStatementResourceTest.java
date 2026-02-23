package de.tum.cit.aet.artemis.hyperion.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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

    private void mockGenerateSuccess() {
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Draft problem statement generated successfully."))))).when(azureOpenAiChatModel)
                .call(any(Prompt.class));
    }

    private void mockGenerateFailure() {
        doThrow(new RuntimeException("AI service unavailable")).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    private void mockChecklistAction() {
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Updated problem statement."))))).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    private void mockChecklistAnalysis() {
        // The full checklist analysis makes three LLM calls (competencies, difficulty, quality).
        // Use sequential returns with any(Prompt.class) to avoid coupling to prompt content.
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"competencies\": [{ \"competencyTitle\": \"Loops\", \"taxonomyLevel\": \"APPLY\", \"confidence\": 0.9, \"whyThisMatches\": \"Loop found\" }]}")))))
                .doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"suggested\": \"EASY\", \"reasoning\": \"Simple\"}")))))
                .doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"issues\": [{ \"category\": \"CLARITY\", \"severity\": \"LOW\", \"description\": \"Vague\", \"suggestedFix\": \"Fix\", \"location\": null }]}")))))
                .when(azureOpenAiChatModel).call(any(Prompt.class));
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
        mockGenerateSuccess();
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
        mockGenerateSuccess();
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
        mockGenerateFailure();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        courseRepository.findById(courseId).orElseThrow();
        String body = "{\"userPrompt\":\"Prompt\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/problem-statements/generate", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.title").value("Failed to generate problem statement: AI service unavailable"))
                .andExpect(jsonPath("$.message").value("error.problemStatementGenerationFailed")).andExpect(jsonPath("$.errorKey").value("problemStatementGenerationFailed"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldAnalyzeChecklistForInstructor() throws Exception {
        long courseId = persistedCourseId;

        mockChecklistAnalysis();

        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        String body = "{\"problemStatementMarkdown\":\"Problem\", \"declaredDifficulty\":\"EASY\", \"exerciseId\":" + persistedExerciseId + "}";

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-analysis", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inferredCompetencies").isArray()).andExpect(jsonPath("$.qualityIssues").isArray());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldAnalyzeChecklistForEditor() throws Exception {
        long courseId = persistedCourseId;

        mockChecklistAnalysis();

        userUtilService.changeUser(TEST_PREFIX + "editor1");

        String body = "{\"problemStatementMarkdown\":\"Problem\", \"declaredDifficulty\":\"EASY\", \"exerciseId\":" + persistedExerciseId + "}";

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-analysis", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inferredCompetencies").isArray()).andExpect(jsonPath("$.qualityIssues").isArray())
                .andExpect(jsonPath("$.difficultyAssessment").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForChecklistAnalysisTutor() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        String body = "{\"problemStatementMarkdown\":\"Problem\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-analysis", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForChecklistAnalysisStudent() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "student1");

        String body = "{\"problemStatementMarkdown\":\"Problem\"}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-analysis", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForChecklistAnalysisCourseMismatch() throws Exception {
        // Create a second course with its own exercise
        Course otherCourse = new Course();
        otherCourse.setTitle("Other Course");
        otherCourse.setInstructorGroupName(TEST_PREFIX + "instructor-other");
        otherCourse = courseRepository.save(otherCourse);

        ProgrammingExercise otherExercise = new ProgrammingExercise();
        otherExercise.setTitle("Other Exercise");
        otherExercise.setCourse(otherCourse);
        otherExercise = programmingExerciseRepository.save(otherExercise);

        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Pass the other exercise's ID to the first course's endpoint
        String body = "{\"problemStatementMarkdown\":\"Problem\", \"declaredDifficulty\":\"EASY\", \"exerciseId\":" + otherExercise.getId() + "}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-analysis", persistedCourseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldAnalyzeChecklistSectionQualityForInstructor() throws Exception {
        long courseId = persistedCourseId;

        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"issues\": [{ \"category\": \"CLARITY\", \"severity\": \"LOW\", \"description\": \"Vague\", \"suggestedFix\": \"Fix\", \"location\": null }]}")))))
                .when(azureOpenAiChatModel).call(any(Prompt.class));

        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        String body = "{\"problemStatementMarkdown\":\"Problem\", \"declaredDifficulty\":\"EASY\", \"exerciseId\":" + persistedExerciseId + "}";

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/checklist-analysis/sections/{section}", courseId, "QUALITY").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qualityIssues").isArray()).andExpect(jsonPath("$.qualityIssues[0].category").value("CLARITY"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldAnalyzeChecklistSectionDifficultyForEditor() throws Exception {
        long courseId = persistedCourseId;

        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"suggested\": \"EASY\", \"reasoning\": \"Simple\"}"))))).when(azureOpenAiChatModel)
                .call(any(Prompt.class));

        userUtilService.changeUser(TEST_PREFIX + "editor1");

        String body = "{\"problemStatementMarkdown\":\"Problem\", \"declaredDifficulty\":\"EASY\"}";

        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/checklist-analysis/sections/{section}", courseId, "DIFFICULTY").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.difficultyAssessment").exists()).andExpect(jsonPath("$.difficultyAssessment.suggested").value("EASY"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForChecklistSectionAnalysisTutor() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        String body = "{\"problemStatementMarkdown\":\"Problem\"}";
        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/checklist-analysis/sections/{section}", courseId, "QUALITY").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForChecklistSectionAnalysisStudent() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "student1");

        String body = "{\"problemStatementMarkdown\":\"Problem\"}";
        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/checklist-analysis/sections/{section}", courseId, "COMPETENCIES").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestForChecklistSectionAnalysisCourseMismatch() throws Exception {
        Course otherCourse = new Course();
        otherCourse.setTitle("Other Course Section");
        otherCourse.setInstructorGroupName(TEST_PREFIX + "instructor-other-section");
        otherCourse = courseRepository.save(otherCourse);

        ProgrammingExercise otherExercise = new ProgrammingExercise();
        otherExercise.setTitle("Other Exercise Section");
        otherExercise.setCourse(otherCourse);
        otherExercise = programmingExerciseRepository.save(otherExercise);

        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        String body = "{\"problemStatementMarkdown\":\"Problem\", \"exerciseId\":" + otherExercise.getId() + "}";
        request.performMvcRequest(
                post("/api/hyperion/courses/{courseId}/checklist-analysis/sections/{section}", persistedCourseId, "QUALITY").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldApplyChecklistActionForInstructor() throws Exception {
        long courseId = persistedCourseId;
        mockChecklistAction();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        String body = "{\"actionType\":\"FIX_QUALITY_ISSUE\",\"problemStatementMarkdown\":\"Problem\","
                + "\"context\":{\"issueDescription\":\"Vague instructions\",\"category\":\"CLARITY\"}}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-actions", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedProblemStatement").isString()).andExpect(jsonPath("$.applied").value(true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldApplyChecklistActionForEditor() throws Exception {
        long courseId = persistedCourseId;
        mockChecklistAction();
        userUtilService.changeUser(TEST_PREFIX + "editor1");

        String body = "{\"actionType\":\"ADAPT_DIFFICULTY\",\"problemStatementMarkdown\":\"Problem\","
                + "\"context\":{\"targetDifficulty\":\"MEDIUM\",\"currentDifficulty\":\"EASY\"}}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-actions", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedProblemStatement").isString()).andExpect(jsonPath("$.applied").value(true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForChecklistActionTutor() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        String body = "{\"actionType\":\"FIX_QUALITY_ISSUE\",\"problemStatementMarkdown\":\"Problem\"," + "\"context\":{\"issueDescription\":\"Vague\",\"category\":\"CLARITY\"}}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-actions", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForChecklistActionStudent() throws Exception {
        long courseId = persistedCourseId;
        userUtilService.changeUser(TEST_PREFIX + "student1");

        String body = "{\"actionType\":\"FIX_QUALITY_ISSUE\",\"problemStatementMarkdown\":\"Problem\"," + "\"context\":{\"issueDescription\":\"Vague\",\"category\":\"CLARITY\"}}";
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/checklist-actions", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
