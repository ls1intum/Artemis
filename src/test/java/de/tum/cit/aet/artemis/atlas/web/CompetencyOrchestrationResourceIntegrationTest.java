package de.tum.cit.aet.artemis.atlas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class CompetencyOrchestrationResourceIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "atlasorchres";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        featureToggleService.enableFeature(Feature.AtlasAgent);
    }

    @AfterEach
    void tearDown() {
        featureToggleService.disableFeature(Feature.AtlasAgent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForProgrammingExercise_featureEnabledNoChatClient_returnsFailedWithMessage() throws Exception {
        // ChatClient is not configured in the test profile, so the orchestrator returns a FAILED
        // result with an explanatory message instead of invoking the LLM. The applied-actions
        // list is present but empty.
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(programmingExercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void runForProgrammingExercise_nonInstructor_returnsForbidden() throws Exception {
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void runForProgrammingExercise_atlasAgentFeatureDisabled_returnsForbidden() throws Exception {
        featureToggleService.disableFeature(Feature.AtlasAgent);
        request.performMvcRequest(post("/api/atlas/orchestrator/programming-exercises/{exerciseId}/run", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
