package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.dto.GocastCourseDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;

/**
 * Pure unit tests for {@link GocastBindingService} — the EP7-driven binding state machine and
 * the IDOR-guarded createBinding / relink logic.
 * <p>
 * No Spring context and no database: the {@link GocastConnectorService} (EP1/EP7) and the
 * {@link GocastCourseBindingRepository} are mocked with Mockito. This gives fast, runnable coverage
 * of the state machine (the integration test that exercises the same logic end-to-end is CI-only
 * because it needs a database).
 * <p>
 * Branches covered:
 * <ul>
 * <li>EP7 returns {@code true} → {@code PENDING → ACTIVE} (persisted).</li>
 * <li>EP7 returns {@code false} → stays {@code PENDING} (not persisted).</li>
 * <li>EP7 throws {@code 403} → stays {@code PENDING} (not persisted; thrown 403 is not definitive).</li>
 * <li>EP7 throws {@code 5xx} / transport → stays {@code PENDING} (not persisted).</li>
 * <li>Already {@code ACTIVE} → EP7 not called, unchanged.</li>
 * <li>Already {@code REVOKED} → EP7 not called, unchanged.</li>
 * <li>createBinding with instructor not administering course → throws AccessForbiddenException.</li>
 * <li>createBinding with instructor administering course → creates PENDING binding.</li>
 * <li>createBinding with existing REVOKED binding → resets to PENDING.</li>
 * <li>createBinding with existing ACTIVE/PENDING binding → throws IllegalStateException.</li>
 * </ul>
 */
class GocastBindingServiceTest {

    private static final long GOCAST_COURSE_ID = 42L;

    private static final long ARTEMIS_COURSE_ID = 7L;

    private static final String INSTRUCTOR_LRZ_ID = "ge12abc";

    private GocastConnectorService connectorService;

    private GocastCourseBindingRepository bindingRepository;

    private GocastBindingService bindingService;

    @BeforeEach
    void setUp() {
        connectorService = mock(GocastConnectorService.class);
        bindingRepository = mock(GocastCourseBindingRepository.class);
        // save() echoes back the entity it was given so the returned status reflects the in-place mutation.
        when(bindingRepository.save(any(GocastCourseBinding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        bindingService = new GocastBindingService(bindingRepository, Optional.of(connectorService));
    }

    private GocastCourseBinding binding(GocastBindingStatus status) {
        GocastCourseBinding binding = new GocastCourseBinding();
        binding.setCourseId(ARTEMIS_COURSE_ID);
        binding.setGocastCourseId(GOCAST_COURSE_ID);
        binding.setGocastCourseSlug("eidi");
        binding.setStatus(status);
        return binding;
    }

    private GocastCourseDTO administeredCourse() {
        return new GocastCourseDTO(GOCAST_COURSE_ID, "Eidi", "eidi", 2026, "W", false, "PUBLIC");
    }

    // ── refreshStatusFromUpstream ─────────────────────────────────────────────

    @Test
    void refreshStatusFromUpstream_pendingAndEp7True_flipsToActiveAndPersists() {
        GocastCourseBinding pending = binding(GocastBindingStatus.PENDING);
        when(connectorService.getBindingStatus(GOCAST_COURSE_ID)).thenReturn(true);

        GocastCourseBinding result = bindingService.refreshStatusFromUpstream(pending);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
        ArgumentCaptor<GocastCourseBinding> saved = ArgumentCaptor.forClass(GocastCourseBinding.class);
        verify(bindingRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
    }

    @Test
    void refreshStatusFromUpstream_pendingAndEp7False_staysPendingAndDoesNotPersist() {
        GocastCourseBinding pending = binding(GocastBindingStatus.PENDING);
        when(connectorService.getBindingStatus(GOCAST_COURSE_ID)).thenReturn(false);

        GocastCourseBinding result = bindingService.refreshStatusFromUpstream(pending);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void refreshStatusFromUpstream_pendingAndEp7Returns403_staysPendingAndDoesNotPersist() {
        // A thrown 403 from EP7 during PENDING refresh is NOT a definitive "unbound" signal —
        // it can indicate a service-account auth/config failure. The binding must stay PENDING
        // so the instructor can retry. REVOKED is only reached from ACTIVE bindings.
        GocastCourseBinding pending = binding(GocastBindingStatus.PENDING);
        when(connectorService.getBindingStatus(GOCAST_COURSE_ID)).thenThrow(new GocastIntegrationException("forbidden", HttpStatus.FORBIDDEN));

        GocastCourseBinding result = bindingService.refreshStatusFromUpstream(pending);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void refreshStatusFromUpstream_pendingAndEp7Returns5xx_staysPendingAndDoesNotPersist() {
        GocastCourseBinding pending = binding(GocastBindingStatus.PENDING);
        when(connectorService.getBindingStatus(GOCAST_COURSE_ID)).thenThrow(new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE));

        GocastCourseBinding result = bindingService.refreshStatusFromUpstream(pending);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void refreshStatusFromUpstream_alreadyActive_doesNotCallEp7OrPersist() {
        GocastCourseBinding active = binding(GocastBindingStatus.ACTIVE);

        GocastCourseBinding result = bindingService.refreshStatusFromUpstream(active);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
        verifyNoInteractions(connectorService);
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void refreshStatusFromUpstream_alreadyRevoked_doesNotCallEp7OrPersist() {
        GocastCourseBinding revoked = binding(GocastBindingStatus.REVOKED);

        GocastCourseBinding result = bindingService.refreshStatusFromUpstream(revoked);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.REVOKED);
        verifyNoInteractions(connectorService);
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void refreshStatusFromUpstream_connectorAbsent_staysPendingAndDoesNotPersist() {
        // When the gocast integration is disabled the connector is not injected; the method must be a safe no-op.
        GocastBindingService serviceWithoutConnector = new GocastBindingService(bindingRepository, Optional.empty());
        GocastCourseBinding pending = binding(GocastBindingStatus.PENDING);

        GocastCourseBinding result = serviceWithoutConnector.refreshStatusFromUpstream(pending);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
        verify(bindingRepository, never()).save(any());
    }

    // ── createBinding — IDOR guard ─────────────────────────────────────────────

    @Test
    void createBinding_instructorDoesNotAdministerCourse_throws403() {
        // EP1 returns a list that does NOT include gocastCourseId=42
        GocastCourseDTO otherCourse = new GocastCourseDTO(99L, "Other", "other", 2026, "W", false, "PUBLIC");
        when(connectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(otherCourse));
        when(bindingRepository.findByCourseId(ARTEMIS_COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bindingService.createBinding(ARTEMIS_COURSE_ID, GOCAST_COURSE_ID, "eidi", INSTRUCTOR_LRZ_ID)).isInstanceOf(AccessForbiddenException.class)
                .hasMessageContaining("does not administer");

        verify(bindingRepository, never()).save(any());
    }

    @Test
    void createBinding_instructorAdministersCourse_succeeds() {
        when(connectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse()));
        when(bindingRepository.findByCourseId(ARTEMIS_COURSE_ID)).thenReturn(Optional.empty());

        GocastCourseBinding result = bindingService.createBinding(ARTEMIS_COURSE_ID, GOCAST_COURSE_ID, "eidi", INSTRUCTOR_LRZ_ID);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
        assertThat(result.getGocastCourseId()).isEqualTo(GOCAST_COURSE_ID);
        verify(bindingRepository).save(any());
    }

    // ── createBinding — MEDIUM-2 relink ───────────────────────────────────────

    @Test
    void createBinding_existingRevokedBinding_resetsToPending() {
        when(connectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse()));
        GocastCourseBinding revoked = binding(GocastBindingStatus.REVOKED);
        when(bindingRepository.findByCourseId(ARTEMIS_COURSE_ID)).thenReturn(Optional.of(revoked));

        GocastCourseBinding result = bindingService.createBinding(ARTEMIS_COURSE_ID, GOCAST_COURSE_ID, "eidi", INSTRUCTOR_LRZ_ID);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
        ArgumentCaptor<GocastCourseBinding> saved = ArgumentCaptor.forClass(GocastCourseBinding.class);
        verify(bindingRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GocastBindingStatus.PENDING);
    }

    @Test
    void createBinding_existingActiveBinding_throwsConflict() {
        when(connectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse()));
        GocastCourseBinding active = binding(GocastBindingStatus.ACTIVE);
        when(bindingRepository.findByCourseId(ARTEMIS_COURSE_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> bindingService.createBinding(ARTEMIS_COURSE_ID, GOCAST_COURSE_ID, "eidi", INSTRUCTOR_LRZ_ID)).isInstanceOf(IllegalStateException.class);

        verify(bindingRepository, never()).save(any());
    }

    @Test
    void createBinding_existingPendingBinding_throwsConflict() {
        when(connectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse()));
        GocastCourseBinding pending = binding(GocastBindingStatus.PENDING);
        when(bindingRepository.findByCourseId(ARTEMIS_COURSE_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> bindingService.createBinding(ARTEMIS_COURSE_ID, GOCAST_COURSE_ID, "eidi", INSTRUCTOR_LRZ_ID)).isInstanceOf(IllegalStateException.class);

        verify(bindingRepository, never()).save(any());
    }
}
