package de.tum.cit.aet.artemis.hyperion.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationMonitoringService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AdminHyperionGenerationMonitoringResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private AuditEventRepository audit;

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void addAdmin() {
        userUtilService.addAdmin("hyperionmonitor");
    }

    @Test
    @WithMockUser(username = "hyperionmonitoradmin", roles = "ADMIN")
    void rejectsBlankReasonWithoutAttemptingCancellation() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/42/generations/job").param("reason", " \n ")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "hyperionmonitoradmin", roles = "ADMIN")
    void auditsTheExactRunEvenWhenCancellationIsNoLongerPossible() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/42/generations/selected-job").param("reason", "Instructor requested a stop"))
                .andExpect(status().isConflict());
        assertThat(audit.find("hyperionmonitoradmin", before, "HYPERION_GENERATION_CANCEL_ATTEMPT"))
                .anySatisfy(event -> assertThat(event.getData()).containsEntry("jobId", "selected-job").containsEntry("reason", "Instructor requested a stop"));
    }

    @Test
    @WithMockUser(username = "hyperionmonitoradmin", roles = "ADMIN")
    void disabledGenerationHasNoActiveRuns() throws Exception {
        assertThat(environment.getProperty("artemis.hyperion.exercise-generation.enabled", Boolean.class, false)).isFalse();
        assertThat(context.getBeansOfType(GenerationMonitoringService.class)).isEmpty();
        request.performMvcRequest(get("/api/hyperion/admin/generations")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void nonAdminsCannotInspectOrCancelOtherRuns() throws Exception {
        request.performMvcRequest(get("/api/hyperion/admin/generations")).andExpect(status().isForbidden());
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/42/generations/job").param("reason", "Stop")).andExpect(status().isForbidden());
    }
}
