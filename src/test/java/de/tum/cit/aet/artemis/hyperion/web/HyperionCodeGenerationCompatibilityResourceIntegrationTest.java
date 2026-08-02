package de.tum.cit.aet.artemis.hyperion.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Real-HTTP authorization matrix and contract test for {@link HyperionCodeGenerationCompatibilityResource}, the honest retirement tombstone of the deleted
 * {@code POST api/hyperion/programming-exercises/{exerciseId}/generate-code} operation.
 * <p>
 * Mirrors the harness of {@link HyperionExerciseGenerationResourceIntegrationTest}: it drives the endpoint through the real Spring Security filter chain and MockMvc (not a mocked
 * resource) so a regression in the route mapping, the {@code @EnforceAtLeastEditorInExercise} aspect, or the {@code HyperionEnabled} conditional would be caught here. No
 * collaborator is mocked; the tombstone has no collaborators to mock in the first place.
 */
class HyperionCodeGenerationCompatibilityResourceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hypcodegencompat";

    /** Instructor outside the course's prefix-restricted groups; exercises the cross-course branch of {@code @EnforceAtLeastEditorInExercise}. */
    private static final String OTHER_PREFIX = "hypcodegencompatother";

    private static final String ENDPOINT = "/api/hyperion/programming-exercises/{exerciseId}/generate-code";

    /** A full legacy {@code CodeGenerationRequestDTO}-shaped body, as a real deployed 8.7.0–9.7 client would have sent it. */
    private static final String LEGACY_SHAPED_BODY = """
            {"repositoryType":"SOLUTION","checkOnly":false,"initialAutoGeneration":false,"selectedFeedbackThreadIds":[1,2]}""";

    /** The bare polling shape old clients used to check for an already-running job without starting a new one. */
    private static final String CHECK_ONLY_BODY = """
            {"checkOnly":true}""";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private long exerciseId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(OTHER_PREFIX, 0, 0, 0, 1);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        userUtilService.addStudentToCourse(TEST_PREFIX + "student1", course);
        userUtilService.addTeachingAssistantToCourse(TEST_PREFIX + "tutor1", course);
        userUtilService.addEditorToCourse(TEST_PREFIX + "editor1", course);
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor1", course);
        ProgrammingExercise programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exerciseId = programmingExerciseRepository.save(programmingExercise).getId();
    }

    @Test
    @WithAnonymousUser
    void generateCode_anonymous_returnsUnauthorized() throws Exception {
        request.performMvcRequest(post(ENDPOINT, exerciseId).contentType(MediaType.APPLICATION_JSON).content(LEGACY_SHAPED_BODY)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void generateCode_student_returnsForbidden() throws Exception {
        request.performMvcRequest(post(ENDPOINT, exerciseId).contentType(MediaType.APPLICATION_JSON).content(LEGACY_SHAPED_BODY)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void generateCode_tutor_returnsForbidden() throws Exception {
        request.performMvcRequest(post(ENDPOINT, exerciseId).contentType(MediaType.APPLICATION_JSON).content(LEGACY_SHAPED_BODY)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generateCode_wrongCourseInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post(ENDPOINT, exerciseId).contentType(MediaType.APPLICATION_JSON).content(LEGACY_SHAPED_BODY)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void generateCode_editor_returnsGoneWithRetirementContract() throws Exception {
        assertRetirementContract(post(ENDPOINT, exerciseId).contentType(MediaType.APPLICATION_JSON).content(LEGACY_SHAPED_BODY));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void generateCode_admin_returnsGoneWithRetirementContract() throws Exception {
        assertRetirementContract(post(ENDPOINT, exerciseId).contentType(MediaType.APPLICATION_JSON).content(LEGACY_SHAPED_BODY));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void generateCode_editorWithCheckOnlyBody_returnsGone() throws Exception {
        // Old clients also polled with {"checkOnly": true} (repositoryType omitted); the tombstone must accept this shape too, even though it never inspects it.
        assertRetirementContract(post(ENDPOINT, exerciseId).contentType(MediaType.APPLICATION_JSON).content(CHECK_ONLY_BODY));
    }

    private void assertRetirementContract(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        request.performMvcRequest(requestBuilder).andExpect(status().isGone()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(410)).andExpect(jsonPath("$.errorKey").value(HyperionCodeGenerationCompatibilityResource.ERROR_KEY))
                .andExpect(jsonPath("$.detail").value(containsString("generate-exercise")))
                .andExpect(header().string("Deprecation", HyperionCodeGenerationCompatibilityResource.DEPRECATION_DATE))
                .andExpect(header().string("Sunset", HyperionCodeGenerationCompatibilityResource.SUNSET_DATE))
                .andExpect(header().string("Link", "</api/hyperion/programming-exercises/" + exerciseId + "/generate-exercise>; rel=\"successor-version\""));
    }
}
