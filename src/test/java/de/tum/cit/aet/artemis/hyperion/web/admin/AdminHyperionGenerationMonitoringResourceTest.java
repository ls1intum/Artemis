package de.tum.cit.aet.artemis.hyperion.web.admin;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import de.tum.cit.aet.artemis.admin.repository.CustomAuditEventRepository;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationMonitoringService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class AdminHyperionGenerationMonitoringResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @MockitoBean
    private GenerationMonitoringService jobs;

    @MockitoBean
    private CustomAuditEventRepository audit;

    @BeforeEach
    void addAdmin() {
        userUtilService.addAdmin("hyperionmonitor");
    }

    @Test
    @WithMockUser(username = "hyperionmonitoradmin", roles = "ADMIN")
    void rejectsBlankReasonWithoutCancellingAnything() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/42/generations/job").param("reason", " \n ")).andExpect(status().isBadRequest());
        verifyNoInteractions(jobs);
    }

    @Test
    @WithMockUser(username = "hyperionmonitoradmin", roles = "ADMIN")
    void auditsBeforeCancellingExactRun() throws Exception {
        when(jobs.cancel(42, "selected-job")).thenReturn(true);
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/42/generations/selected-job").param("reason", "Instructor requested a stop"))
                .andExpect(status().isAccepted());
        var ordered = inOrder(audit, jobs);
        ordered.verify(audit).add(
                org.mockito.ArgumentMatchers.argThat(event -> event.getType().equals("HYPERION_GENERATION_CANCEL_ATTEMPT") && event.getData().get("jobId").equals("selected-job")));
        ordered.verify(jobs).cancel(42, "selected-job");
    }

    @Test
    @WithMockUser(username = "hyperionmonitoradmin", roles = "ADMIN")
    void staleOrPersistingRunIsAConflictNotASuccess() throws Exception {
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/42/generations/old-job").param("reason", "Stop this run")).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void nonAdminsCannotInspectOrCancelOtherRuns() throws Exception {
        request.performMvcRequest(get("/api/hyperion/admin/exercises/generations")).andExpect(status().isForbidden());
        request.performMvcRequest(delete("/api/hyperion/admin/exercises/42/generations/job").param("reason", "Stop")).andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }
}
