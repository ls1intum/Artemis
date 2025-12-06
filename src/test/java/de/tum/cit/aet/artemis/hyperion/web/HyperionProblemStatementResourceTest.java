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
}
