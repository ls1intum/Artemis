package de.tum.cit.aet.artemis.hyperion.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
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

    @Test
    void checkExerciseConsistency_returnsOkWithBody() throws Exception {
        long exerciseId = 42L;
        var user = new User();
        user.setLogin("instructor");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        var exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(exercise);

        var issues = List.of(new ConsistencyIssueDTO(Severity.MEDIUM, "METHOD_PARAMETER_MISMATCH", "desc", "fix", List.of()));
        when(consistencyCheckService.checkConsistency(eq(user), eq(exercise))).thenReturn(new ConsistencyCheckResponseDTO(issues, true, "Found 1 consistency issue(s)"));

        mockMvc.perform(post("/api/hyperion/exercises/{exerciseId}/consistency-check", exerciseId)).andExpect(status().isOk()).andExpect(jsonPath("$.hasIssues").value(true))
                .andExpect(jsonPath("$.issues[0].category").value("METHOD_PARAMETER_MISMATCH"));
    }

    @Test
    void rewriteProblemStatement_returnsOkWithBody() throws Exception {
        long courseId = 5L;
        var user = new User();
        user.setLogin("instructor");
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        var course = new Course();
        course.setId(courseId);
        when(courseRepository.findByIdElseThrow(courseId)).thenReturn(course);

        when(rewriteService.rewriteProblemStatement(eq(user), eq(course), eq("Original"))).thenReturn(new ProblemStatementRewriteResponseDTO("Rewritten", true));

        String body = "{\"problemStatementText\":\"Original\"}";
        mockMvc.perform(post("/api/hyperion/courses/{courseId}/problem-statement-rewrite", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rewrittenText").value("Rewritten")).andExpect(jsonPath("$.improved").value(true));
    }
}
