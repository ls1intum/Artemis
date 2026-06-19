package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;

/**
 * Pure unit tests for {@link GocastBindingService#refreshStatusFromUpstream} — the EP7-driven
 * binding state machine.
 * <p>
 * No Spring context and no database: the {@link GocastConnectorService} (EP7) and the
 * {@link GocastCourseBindingRepository} are mocked with Mockito. This gives fast, runnable coverage
 * of the state machine (the integration test that exercises the same logic end-to-end is CI-only
 * because it needs a database).
 * <p>
 * Branches covered:
 * <ul>
 * <li>EP7 returns {@code true} → {@code PENDING → ACTIVE} (persisted).</li>
 * <li>EP7 returns {@code false} → stays {@code PENDING} (not persisted).</li>
 * <li>EP7 throws {@code 403} → {@code PENDING → REVOKED} (persisted).</li>
 * <li>EP7 throws {@code 5xx} / transport → stays {@code PENDING} (not persisted).</li>
 * <li>Already {@code ACTIVE} → EP7 not called, unchanged.</li>
 * <li>Already {@code REVOKED} → EP7 not called, unchanged.</li>
 * </ul>
 */
class GocastBindingServiceTest {

    private static final long GOCAST_COURSE_ID = 42L;

    private static final long ARTEMIS_COURSE_ID = 7L;

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
    void refreshStatusFromUpstream_pendingAndEp7Returns403_flipsToRevokedAndPersists() {
        GocastCourseBinding pending = binding(GocastBindingStatus.PENDING);
        when(connectorService.getBindingStatus(GOCAST_COURSE_ID)).thenThrow(new GocastIntegrationException("forbidden", HttpStatus.FORBIDDEN));

        GocastCourseBinding result = bindingService.refreshStatusFromUpstream(pending);

        assertThat(result.getStatus()).isEqualTo(GocastBindingStatus.REVOKED);
        ArgumentCaptor<GocastCourseBinding> saved = ArgumentCaptor.forClass(GocastCourseBinding.class);
        verify(bindingRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GocastBindingStatus.REVOKED);
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
}
