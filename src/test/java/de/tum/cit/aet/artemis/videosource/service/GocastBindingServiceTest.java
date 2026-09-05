package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingConnectionStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastBindingDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastApprovalAttemptRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;

class GocastBindingServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final Instant NOW = Instant.parse("2026-09-05T03:00:00Z");

    @Autowired
    private GocastConnectionRepository connectionRepository;

    @Autowired
    private GocastCourseBindingRepository bindingRepository;

    @Autowired
    private GocastApprovalAttemptRepository attemptRepository;

    private GocastConnectorService connector;

    private GocastBindingService service;

    private Course course;

    @BeforeEach
    void setUp() {
        connector = mock(GocastConnectorService.class);
        service = new GocastBindingService(connectionRepository, courseRepository, connector, "https://artemis.example/api/videosource/public/gocast/approval/callback",
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        course = courseUtilService.addEmptyCourse();
    }

    @AfterEach
    void cleanUp() {
        attemptRepository.deleteAll();
        bindingRepository.deleteAll();
    }

    @Test
    void remoteCreateRunsAfterPendingCommitAndOutsideTransaction() {
        when(connector.createApproval(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(attemptRepository.findByCourseId(course.getId())).isPresent();
            return new GocastConnectorService.CreatedApproval("request", "https://live.example/integration/approve/request", NOW.plusSeconds(900));
        });

        var approval = service.startApproval(course.getId());

        assertThat(approval.approvalUrl()).isEqualTo("https://live.example/integration/approve/request");
        assertThat(attemptRepository.findByCourseId(course.getId())).get().satisfies(attempt -> assertThat(attempt.getRequestId()).isEqualTo("request"));
    }

    @Test
    void remoteRevokeRunsOutsideTransactionAndFailurePreservesBindingForRetry() {
        createBinding();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }).when(connector).revokeGrant(37, 23);

        try {
            service.unlink(course.getId());
        }
        catch (GocastIntegrationException ignored) {
            // The endpoint exposes this as a retryable 503.
        }

        assertThat(bindingRepository.findByCourseId(course.getId())).isPresent();
    }

    @Test
    void statusFailureKeepsSavedConnectionVisible() {
        createBinding();
        when(connector.getGrantStatus(37, 23)).thenThrow(new GocastIntegrationException("forbidden", HttpStatus.FORBIDDEN));

        GocastBindingDTO result = service.getBinding(course.getId());

        assertThat(result.status()).isEqualTo(GocastBindingConnectionStatus.ACTIVE);
        assertThat(result.courseName()).isEqualTo("Algorithms");
        assertThat(result.upstreamUnavailable()).isTrue();
        assertThat(bindingRepository.findByCourseId(course.getId())).isPresent();
    }

    private void createBinding() {
        connectionRepository.startAttempt(course.getId(), "state", NOW.plusSeconds(900));
        connectionRepository.attachRemoteRequest(course.getId(), "state", "request", NOW.plusSeconds(900));
        connectionRepository.claimAttempt("state", "request", NOW);
        connectionRepository.completeAttempt("state", "request", new GocastVerifiedCourseDTO(17, 23, 37, "algorithms", "Algorithms", "loggedin"), NOW);
    }
}
