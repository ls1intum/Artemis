package de.tum.cit.aet.artemis.hyperion.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;

class AdminHyperionLiveSaveRecoveryTest {

    private final GenerationJobService jobs = mock(GenerationJobService.class);

    private final GenerationLiveSaveService liveSaves = mock(GenerationLiveSaveService.class);

    private final AuditEventRepository audit = mock(AuditEventRepository.class);

    private final AdminHyperionGenerationResource resource = new AdminHyperionGenerationResource(Optional.of(jobs), liveSaves, audit);

    private final GenerationLiveSaveService.LiveSaveSlot slot = new GenerationLiveSaveService.LiveSaveSlot("live-save:exact", "owner", Instant.parse("2026-10-15T12:00:00Z"));

    @BeforeEach
    void setUp() {
        when(liveSaves.getSlotInfo(42)).thenReturn(Optional.of(slot));
    }

    @Test
    void inspectLiveSaveWithoutClaimingProcessLiveness() {
        var response = resource.getWedgedSlot(42);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isEqualTo(slot.token());
        assertThat(response.getBody().kind()).isEqualTo("LIVE_SAVE");
        assertThat(response.getBody().ownerLeftCluster()).isFalse();
        verifyNoInteractions(jobs, audit);
    }

    @Test
    void auditBeforeExactLiveSaveRecovery() {
        when(liveSaves.recoverSlot(42, slot.token())).thenReturn(true);
        assertThat(resource.recoverWedgedSlot(42, slot.token(), "Stopped owner and reconciled repositories").getStatusCode().value()).isEqualTo(204);
        var order = inOrder(audit, liveSaves);
        order.verify(audit).add(any(AuditEvent.class));
        order.verify(liveSaves).recoverSlot(42, slot.token());
        verifyNoInteractions(jobs);
    }

    @Test
    void rejectSupersededTokenWithoutRecoveryOrAudit() {
        assertThat(resource.recoverWedgedSlot(42, "live-save:old", "Owner stopped").getStatusCode().value()).isEqualTo(404);
        verify(liveSaves, never()).recoverSlot(42, "live-save:old");
        verifyNoInteractions(jobs, audit);
    }

    @Test
    void rejectBlankReasonBeforeInspectingOrRecovering() {
        assertThat(resource.recoverWedgedSlot(42, slot.token(), "\n\t").getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(liveSaves, jobs, audit);
    }

    @Test
    void preserveJobRecoveryWhenNoLiveSaveExists() {
        when(liveSaves.getSlotInfo(42)).thenReturn(Optional.empty());
        when(jobs.getWedgedSlotInfo(42))
                .thenReturn(Optional.of(new GenerationJobService.WedgedSlotInfo(42, "job", GenerationJobService.WedgedSlotKind.GENERATION, "job-owner", slot.startedAt(), false)));
        when(jobs.recoverWedgedSlot(42, "job")).thenReturn(true);
        assertThat(resource.recoverWedgedSlot(42, "job", "Writer stopped").getStatusCode().value()).isEqualTo(204);
        verify(jobs).recoverWedgedSlot(42, "job");
        verify(liveSaves, never()).recoverSlot(42, "job");
    }
}
