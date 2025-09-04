package de.tum.cit.aet.artemis.hyperion.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceRoleInCourseAspect;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceRoleInExerciseAspect;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.Severity;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRewriteService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

@WebMvcTest(HyperionReviewAndRefineResource.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("hyperion")
@Import({ AopAutoConfiguration.class, HyperionReviewAndRefineResourceTest.SecurityTestConfig.class })
class HyperionReviewAndRefineResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CourseRepository courseRepository;

    @MockBean
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @MockBean
    private HyperionConsistencyCheckService consistencyCheckService;

    @MockBean
    private HyperionProblemStatementRewriteService rewriteService;

    @MockBean
    private TeamRepository teamRepository;

    @TestConfiguration
    static class SecurityTestConfig {

        @Bean
        EnforceRoleInExerciseAspect enforceRoleInExerciseAspect(AuthorizationCheckService s) {
            return new EnforceRoleInExerciseAspect(s);
        }

        @Bean
        EnforceRoleInCourseAspect enforceRoleInCourseAspect(AuthorizationCheckService s) {
            return new EnforceRoleInCourseAspect(s);
        }

        @Bean
        AuthorizationCheckService authorizationCheckService(UserRepository userRepository, CourseRepository courseRepository, TeamRepository teamRepository) {
            return new AuthorizationCheckService(userRepository, courseRepository, teamRepository);
        }
    }

    @Test
    @WithMockUser(username = "admin")
    void checkExerciseConsistency_returnsOkWithBody_when_user_is_admin() throws Exception {
        long exerciseId = 42L;
        var user = new User();
        user.setLogin("admin");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        when(userRepository.isAtLeastEditorInExercise("admin", exerciseId)).thenReturn(true);

        var exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(exercise);

        var issues = List.of(new ConsistencyIssueDTO(Severity.MEDIUM, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "desc", "fix", List.of()));
        when(consistencyCheckService.checkConsistency(user, exercise)).thenReturn(new ConsistencyCheckResponseDTO(issues));

        mockMvc.perform(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.issues[0].category").value("METHOD_PARAMETER_MISMATCH"));
    }

    @Test
    @WithMockUser(username = "instructor")
    void checkExerciseConsistency_returnsOkWithBody_when_user_is_instructor() throws Exception {
        long exerciseId = 42L;
        var user = new User();
        user.setLogin("instructor");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        when(userRepository.isAtLeastEditorInExercise("instructor", exerciseId)).thenReturn(true);

        var exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(exercise);

        var issues = List.of(new ConsistencyIssueDTO(Severity.MEDIUM, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "desc", "fix", List.of()));
        when(consistencyCheckService.checkConsistency(user, exercise)).thenReturn(new ConsistencyCheckResponseDTO(issues));

        mockMvc.perform(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.issues[0].category").value("METHOD_PARAMETER_MISMATCH"));
    }

    @Test
    @WithMockUser(username = "editor")
    void checkExerciseConsistency_returnsOkWithBody_when_user_is_editor() throws Exception {
        long exerciseId = 42L;
        var user = new User();
        user.setLogin("editor");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        when(userRepository.isAtLeastEditorInExercise("editor", exerciseId)).thenReturn(true);

        var exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(exercise);

        var issues = List.of(new ConsistencyIssueDTO(Severity.MEDIUM, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "desc", "fix", List.of()));
        when(consistencyCheckService.checkConsistency(user, exercise)).thenReturn(new ConsistencyCheckResponseDTO(issues));

        mockMvc.perform(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.issues[0].category").value("METHOD_PARAMETER_MISMATCH"));
    }

    @Test
    @WithMockUser(username = "tutor")
    void checkExerciseConsistency_forbidden_when_user_is_tutor() throws Exception {
        long exerciseId = 42L;

        var user = new User();
        user.setLogin("tutor");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        var ex = new ProgrammingExercise();
        ex.setId(exerciseId);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(ex);

        when(userRepository.isAtLeastEditorInExercise("tutor", exerciseId)).thenReturn(false);

        mockMvc.perform(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isForbidden());

        verifyNoInteractions(consistencyCheckService);
    }

    @Test
    @WithMockUser(username = "student")
    void checkExerciseConsistency_forbidden_when_user_is_student() throws Exception {
        long exerciseId = 42L;

        var user = new User();
        user.setLogin("student");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        var ex = new ProgrammingExercise();
        ex.setId(exerciseId);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(ex);

        when(userRepository.isAtLeastEditorInExercise("student", exerciseId)).thenReturn(false);

        mockMvc.perform(post("/api/hyperion/programming-exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isForbidden());

        verifyNoInteractions(consistencyCheckService);
    }

    @Test
    @WithMockUser(username = "admin")
    void rewriteProblemStatement_returnsOkWithBody_when_user_is_admin() throws Exception {
        long courseId = 5L;
        var user = new User();
        user.setLogin("admin");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        when(userRepository.isAtLeastEditorInCourse("admin", courseId)).thenReturn(true);

        var course = new Course();
        course.setId(courseId);
        when(courseRepository.findByIdElseThrow(courseId)).thenReturn(course);

        when(rewriteService.rewriteProblemStatement(eq(user), eq(course), eq("Original"))).thenReturn(new ProblemStatementRewriteResponseDTO("Rewritten", true));

        String body = "{\"problemStatementText\":\"Original\"}";
        mockMvc.perform(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rewrittenText").value("Rewritten")).andExpect(jsonPath("$.improved").value(true));
    }

    @Test
    @WithMockUser(username = "instructor")
    void rewriteProblemStatement_returnsOkWithBody_when_user_is_instructor() throws Exception {
        long courseId = 5L;
        var user = new User();
        user.setLogin("instructor");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        when(userRepository.isAtLeastEditorInCourse("instructor", courseId)).thenReturn(true);

        var course = new Course();
        course.setId(courseId);
        when(courseRepository.findByIdElseThrow(courseId)).thenReturn(course);

        when(rewriteService.rewriteProblemStatement(eq(user), eq(course), eq("Original"))).thenReturn(new ProblemStatementRewriteResponseDTO("Rewritten", true));

        String body = "{\"problemStatementText\":\"Original\"}";
        mockMvc.perform(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rewrittenText").value("Rewritten")).andExpect(jsonPath("$.improved").value(true));
    }

    @Test
    @WithMockUser(username = "editor")
    void rewriteProblemStatement_returnsOkWithBody_when_user_is_editor() throws Exception {
        long courseId = 5L;
        var user = new User();
        user.setLogin("editor");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        when(userRepository.isAtLeastEditorInCourse("editor", courseId)).thenReturn(true);

        var course = new Course();
        course.setId(courseId);
        when(courseRepository.findByIdElseThrow(courseId)).thenReturn(course);

        when(rewriteService.rewriteProblemStatement(eq(user), eq(course), eq("Original"))).thenReturn(new ProblemStatementRewriteResponseDTO("Rewritten", true));

        String body = "{\"problemStatementText\":\"Original\"}";
        mockMvc.perform(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rewrittenText").value("Rewritten")).andExpect(jsonPath("$.improved").value(true));
    }

    @Test
    @WithMockUser(username = "tutor")
    void rewriteProblemStatement_forbidden_when_user_is_tutor() throws Exception {
        long courseId = 5L;
        var user = new User();
        user.setLogin("tutor");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        when(userRepository.isAtLeastEditorInCourse("tutor", courseId)).thenReturn(false);

        var course = new Course();
        course.setId(courseId);
        when(courseRepository.findByIdElseThrow(courseId)).thenReturn(course);

        when(rewriteService.rewriteProblemStatement(eq(user), eq(course), eq("Original"))).thenReturn(new ProblemStatementRewriteResponseDTO("Rewritten", true));

        String body = "{\"problemStatementText\":\"Original\"}";
        mockMvc.perform(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        verifyNoInteractions(rewriteService);
    }

    @Test
    @WithMockUser(username = "student")
    void rewriteProblemStatement_forbidden_when_user_is_student() throws Exception {
        long courseId = 5L;
        var user = new User();
        user.setLogin("student");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        when(userRepository.isAtLeastEditorInCourse("student", courseId)).thenReturn(false);

        var course = new Course();
        course.setId(courseId);
        when(courseRepository.findByIdElseThrow(courseId)).thenReturn(course);

        when(rewriteService.rewriteProblemStatement(eq(user), eq(course), eq("Original"))).thenReturn(new ProblemStatementRewriteResponseDTO("Rewritten", true));

        String body = "{\"problemStatementText\":\"Original\"}";
        mockMvc.perform(post("/api/hyperion/courses/{courseId}/problem-statements/rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        verifyNoInteractions(rewriteService);
    }
}
