package de.tum.cit.aet.artemis.hyperion.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEventRepository;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationMonitoringService;

class AdminHyperionGenerationMonitoringResourceTest {

    private final GenerationMonitoringService jobs = mock(GenerationMonitoringService.class);

    private final AuditEventRepository audit = mock(AuditEventRepository.class);

    private final AdminHyperionGenerationMonitoringResource resource = new AdminHyperionGenerationMonitoringResource(Optional.of(jobs), audit);

    @Test
    void rejectsBlankReasonWithoutCancellingAnything() {
        assertThat(resource.cancelGeneration(1, "job", " \n ").getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(jobs, audit);
    }

    @Test
    void auditsBeforeCancellingExactRun() {
        when(jobs.cancel(42, "selected-job")).thenReturn(true);
        assertThat(resource.cancelGeneration(42, "selected-job", "Instructor requested a stop").getStatusCode().value()).isEqualTo(202);
        var ordered = inOrder(audit, jobs);
        ordered.verify(audit).add(
                org.mockito.ArgumentMatchers.argThat(event -> event.getType().equals("HYPERION_GENERATION_CANCEL_ATTEMPT") && event.getData().get("jobId").equals("selected-job")));
        ordered.verify(jobs).cancel(42, "selected-job");
    }

    @Test
    void staleOrPersistingRunIsAConflictNotASuccess() {
        assertThat(resource.cancelGeneration(42, "old-job", "Stop this run").getStatusCode().value()).isEqualTo(409);
    }
}
