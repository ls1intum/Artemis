package de.tum.cit.aet.artemis.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.GenerationSandboxSessionDTO;
import de.tum.cit.aet.artemis.buildagent.service.RemoteInteractiveSandboxClient;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.SharedQueueManagementService;

class AdminBuildJobQueueResourceTest {

    @Test
    void returnsLiveGenerationSandboxesForKnownAgent() {
        DistributedDataAccessService dataAccess = mock(DistributedDataAccessService.class);
        RemoteInteractiveSandboxClient sandboxClient = mock(RemoteInteractiveSandboxClient.class);
        GenerationJobService jobService = mock(GenerationJobService.class);
        BuildAgentInformation agent = new BuildAgentInformation(new BuildAgentDTO("agent-1", "address", "Agent 1"), 4, 0, List.of(), BuildAgentStatus.IDLE, null, null, 0, 2, 2);
        GenerationSandboxSessionDTO session = new GenerationSandboxSessionDTO("agent-1::container", "job-1", 12L, "Sorting exercise", 3L, "instructor", "GENERATE", Instant.EPOCH,
                Instant.EPOCH);
        when(dataAccess.getBuildAgentInformation()).thenReturn(List.of(agent));
        when(sandboxClient.listSessions("agent-1")).thenReturn(List.of(session));
        AdminBuildJobQueueResource resource = resource(dataAccess, sandboxClient, jobService);

        var response = resource.getGenerationSandboxes("agent-1");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsExactly(session);
    }

    @Test
    void cancelsGenerationThroughTheParentJob() {
        DistributedDataAccessService dataAccess = mock(DistributedDataAccessService.class);
        GenerationJobService jobService = mock(GenerationJobService.class);
        when(jobService.requestSystemCancellation(12L, "job-1")).thenReturn(true);
        AdminBuildJobQueueResource resource = resource(dataAccess, mock(RemoteInteractiveSandboxClient.class), jobService);

        var response = resource.cancelGenerationJob(12L, "job-1");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(jobService).requestSystemCancellation(12L, "job-1");
    }

    @Test
    void returnsEmptyWithoutRelayForAgentWithSandboxHostingDisabled() {
        DistributedDataAccessService dataAccess = mock(DistributedDataAccessService.class);
        RemoteInteractiveSandboxClient sandboxClient = mock(RemoteInteractiveSandboxClient.class);
        when(dataAccess.getBuildAgentInformation()).thenReturn(List.of(agent("agent-1", 0)));
        AdminBuildJobQueueResource resource = resource(dataAccess, Optional.of(sandboxClient), Optional.empty());

        var response = resource.getGenerationSandboxes("agent-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
        verify(sandboxClient, never()).listSessions("agent-1");
    }

    @Test
    void returnsNotFoundForUnknownAgent() {
        DistributedDataAccessService dataAccess = mock(DistributedDataAccessService.class);
        when(dataAccess.getBuildAgentInformation()).thenReturn(List.of());

        var response = resource(dataAccess, Optional.empty(), Optional.empty()).getGenerationSandboxes("missing");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void returnsServiceUnavailableWhenRelayClientIsMissing() {
        DistributedDataAccessService dataAccess = mock(DistributedDataAccessService.class);
        when(dataAccess.getBuildAgentInformation()).thenReturn(List.of(agent("agent-1", 2)));

        var response = resource(dataAccess, Optional.empty(), Optional.empty()).getGenerationSandboxes("agent-1");

        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void returnsServiceUnavailableWhenAgentRelayIsUnreachable() {
        DistributedDataAccessService dataAccess = mock(DistributedDataAccessService.class);
        RemoteInteractiveSandboxClient sandboxClient = mock(RemoteInteractiveSandboxClient.class);
        when(dataAccess.getBuildAgentInformation()).thenReturn(List.of(agent("agent-1", 2)));
        when(sandboxClient.listSessions("agent-1")).thenThrow(new LocalCIException("unreachable"));

        var response = resource(dataAccess, Optional.of(sandboxClient), Optional.empty()).getGenerationSandboxes("agent-1");

        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void returnsNotFoundWhenGenerationCannotBeCancelled() {
        GenerationJobService jobService = mock(GenerationJobService.class);

        var response = resource(mock(DistributedDataAccessService.class), Optional.empty(), Optional.of(jobService)).cancelGenerationJob(12L, "missing");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void returnsExternalMutationForDiagnosis() {
        GenerationJobService jobService = mock(GenerationJobService.class);
        GenerationJobService.ExternalMutationInfo info = new GenerationJobService.ExternalMutationInfo(12L, "external-mutation-token", "node-1", Instant.EPOCH);
        when(jobService.getExternalMutationInfo(12L)).thenReturn(Optional.of(info));
        AdminBuildJobQueueResource resource = resource(mock(DistributedDataAccessService.class), Optional.empty(), Optional.of(jobService));

        var response = resource.getExternalMutation(12L);

        assertThat(response.getBody()).isEqualTo(info);
    }

    @Test
    void recoversOnlyMatchingExternalMutation() {
        GenerationJobService jobService = mock(GenerationJobService.class);
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        GenerationJobService.ExternalMutationInfo info = new GenerationJobService.ExternalMutationInfo(12L, "external-mutation-token", "node-1", Instant.EPOCH);
        when(jobService.getExternalMutationInfo(12L)).thenReturn(Optional.of(info));
        when(jobService.recoverExternalMutationSlot(12L, "external-mutation-token")).thenReturn(true);
        AdminBuildJobQueueResource resource = resource(mock(DistributedDataAccessService.class), Optional.empty(), Optional.of(jobService), auditEventRepository);

        assertThat(resource.recoverExternalMutation(12L, "wrong", "owner terminated").getStatusCode().value()).isEqualTo(404);
        assertThat(resource.recoverExternalMutation(12L, "external-mutation-token", " owner\r\nterminated ").getStatusCode().value()).isEqualTo(204);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        InOrder recoveryOrder = inOrder(auditEventRepository, jobService);
        recoveryOrder.verify(auditEventRepository).add(event.capture());
        recoveryOrder.verify(jobService).recoverExternalMutationSlot(12L, "external-mutation-token");
        assertThat(event.getValue().getType()).isEqualTo(Constants.HYPERION_EXTERNAL_MUTATION_RECOVERY_ATTEMPT);
        assertThat(event.getValue().getData()).containsEntry("exerciseId", 12L).containsEntry("ownerNodeId", "node-1").containsEntry("reason", "owner terminated");
    }

    @Test
    void externalMutationRecoveryRequiresAnAuditReason() {
        GenerationJobService jobService = mock(GenerationJobService.class);
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        AdminBuildJobQueueResource resource = resource(mock(DistributedDataAccessService.class), Optional.empty(), Optional.of(jobService), auditEventRepository);

        assertThat(resource.recoverExternalMutation(12L, "external-mutation-token", "  ").getStatusCode().value()).isEqualTo(400);
        verify(jobService, never()).recoverExternalMutationSlot(12L, "external-mutation-token");
        verify(auditEventRepository, never()).add(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void externalMutationRecoveryFailsClosedWhenAuditPersistenceFails() {
        GenerationJobService jobService = mock(GenerationJobService.class);
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        GenerationJobService.ExternalMutationInfo info = new GenerationJobService.ExternalMutationInfo(12L, "external-mutation-token", "node-1", Instant.EPOCH);
        when(jobService.getExternalMutationInfo(12L)).thenReturn(Optional.of(info));
        doThrow(new IllegalStateException("audit unavailable")).when(auditEventRepository).add(org.mockito.ArgumentMatchers.any());
        AdminBuildJobQueueResource resource = resource(mock(DistributedDataAccessService.class), Optional.empty(), Optional.of(jobService), auditEventRepository);

        assertThatThrownBy(() -> resource.recoverExternalMutation(12L, "external-mutation-token", "owner terminated")).isInstanceOf(IllegalStateException.class);
        verify(jobService, never()).recoverExternalMutationSlot(12L, "external-mutation-token");
    }

    @Test
    void externalMutationRecoveryAuditsAnUnknownLegacyOwner() {
        GenerationJobService jobService = mock(GenerationJobService.class);
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        GenerationJobService.ExternalMutationInfo info = new GenerationJobService.ExternalMutationInfo(12L, "external-mutation-token", null, Instant.EPOCH);
        when(jobService.getExternalMutationInfo(12L)).thenReturn(Optional.of(info));
        when(jobService.recoverExternalMutationSlot(12L, "external-mutation-token")).thenReturn(true);
        AdminBuildJobQueueResource resource = resource(mock(DistributedDataAccessService.class), Optional.empty(), Optional.of(jobService), auditEventRepository);

        assertThat(resource.recoverExternalMutation(12L, "external-mutation-token", "legacy owner unavailable").getStatusCode().value()).isEqualTo(204);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).add(event.capture());
        assertThat(event.getValue().getData()).containsEntry("ownerNodeId", "unknown");
    }

    private static AdminBuildJobQueueResource resource(DistributedDataAccessService dataAccess, RemoteInteractiveSandboxClient sandboxClient, GenerationJobService jobService) {
        return new AdminBuildJobQueueResource(mock(SharedQueueManagementService.class), mock(BuildJobRepository.class), dataAccess, Optional.of(sandboxClient),
                Optional.of(jobService), mock(AuditEventRepository.class));
    }

    private static AdminBuildJobQueueResource resource(DistributedDataAccessService dataAccess, Optional<RemoteInteractiveSandboxClient> sandboxClient,
            Optional<GenerationJobService> jobService) {
        return resource(dataAccess, sandboxClient, jobService, mock(AuditEventRepository.class));
    }

    private static AdminBuildJobQueueResource resource(DistributedDataAccessService dataAccess, Optional<RemoteInteractiveSandboxClient> sandboxClient,
            Optional<GenerationJobService> jobService, AuditEventRepository auditEventRepository) {
        return new AdminBuildJobQueueResource(mock(SharedQueueManagementService.class), mock(BuildJobRepository.class), dataAccess, sandboxClient, jobService,
                auditEventRepository);
    }

    private static BuildAgentInformation agent(String name, int maxSandboxSlots) {
        return new BuildAgentInformation(new BuildAgentDTO(name, "address", name), 4, 0, List.of(), BuildAgentStatus.IDLE, null, null, 0, 0, maxSandboxSlots);
    }
}
