package de.tum.cit.aet.artemis.videosource.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.videosource.config.GocastConfiguration.GocastSettings;
import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.domain.GocastApprovalAttemptStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingConnectionStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastApprovalResultDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastApprovalStartDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastBindingDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository.AttemptSnapshot;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository.BindingSnapshot;

@Lazy
@Service
@Conditional(GocastEnabled.class)
public class GocastBindingService {

    private static final Duration APPROVAL_LIFETIME = Duration.ofMinutes(15);

    private final GocastConnectionRepository connectionRepository;

    private final CourseRepository courseRepository;

    private final GocastConnectorService connectorService;

    private final String callbackUrl;

    private final Clock clock;

    private final SecureRandom secureRandom;

    @Autowired
    public GocastBindingService(GocastConnectionRepository connectionRepository, CourseRepository courseRepository, GocastConnectorService connectorService,
            GocastSettings settings) {
        this(connectionRepository, courseRepository, connectorService, settings.callbackUri().toString(), Clock.systemUTC(), new SecureRandom());
    }

    GocastBindingService(GocastConnectionRepository connectionRepository, CourseRepository courseRepository, GocastConnectorService connectorService, String callbackUrl,
            Clock clock, SecureRandom secureRandom) {
        this.connectionRepository = connectionRepository;
        this.courseRepository = courseRepository;
        this.connectorService = connectorService;
        this.callbackUrl = callbackUrl;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * Returns local connection state and safely refreshes an active remote grant.
     *
     * @param courseId the Artemis course identifier
     * @return the instructor-facing connection state
     */
    public GocastBindingDTO getBinding(long courseId) {
        var savedBinding = connectionRepository.getBindingSnapshot(courseId);
        if (savedBinding.isPresent()) {
            BindingSnapshot snapshot = savedBinding.get();
            if (snapshot.status() == GocastBindingStatus.UNLINKING) {
                return binding(snapshot, GocastBindingConnectionStatus.ACTIVE, false);
            }
            try {
                var remoteStatus = connectorService.getGrantStatus(snapshot.gocastCourseId(), snapshot.grantId());
                if (!connectionRepository.updateGrantStatus(snapshot, remoteStatus)) {
                    return currentLocalState(courseId, null);
                }
                if (Boolean.FALSE.equals(remoteStatus.active())) {
                    return binding(snapshot, GocastBindingConnectionStatus.REVOKED, false);
                }
                return new GocastBindingDTO(true, GocastBindingConnectionStatus.ACTIVE, remoteStatus.courseId(), remoteStatus.courseName(), remoteStatus.courseSlug(),
                        remoteStatus.courseVisibility(), null, false);
            }
            catch (GocastIntegrationException exception) {
                return currentLocalState(courseId, snapshot);
            }
        }

        return currentLocalState(courseId, null);
    }

    private GocastBindingDTO currentLocalState(long courseId, BindingSnapshot unavailableClaim) {
        var savedBinding = connectionRepository.getBindingSnapshot(courseId);
        if (savedBinding.isPresent()) {
            BindingSnapshot current = savedBinding.get();
            GocastBindingConnectionStatus status = current.status() == GocastBindingStatus.REVOKED ? GocastBindingConnectionStatus.REVOKED : GocastBindingConnectionStatus.ACTIVE;
            return binding(current, status, current.equals(unavailableClaim));
        }

        var attempt = connectionRepository.getAttemptSnapshot(courseId);
        if (attempt.isEmpty()) {
            return unlinked();
        }
        AttemptSnapshot current = attempt.get();
        GocastBindingConnectionStatus status = current.expiresAt().isAfter(clock.instant()) && current.status() != GocastApprovalAttemptStatus.EXPIRED
                ? GocastBindingConnectionStatus.PENDING
                : GocastBindingConnectionStatus.EXPIRED;
        return new GocastBindingDTO(true, status, null, null, null, null, current.expiresAt(), false);
    }

    /**
     * Starts a short-lived approval and returns its validated GoCast URL.
     *
     * @param courseId the Artemis course identifier
     * @return the approval URL and expiry
     */
    public GocastApprovalStartDTO startApproval(long courseId) {
        String courseLabel = courseRepository.findByIdElseThrow(courseId).getTitle();
        String state = randomOpaqueValue();
        String stateHash = hash(state);
        connectionRepository.startAttempt(courseId, stateHash, clock.instant().plus(APPROVAL_LIFETIME));

        var approval = connectorService.createApproval(state, courseLabel, callbackUrl);
        if (!connectionRepository.attachRemoteRequest(courseId, stateHash, approval.requestId(), approval.expiresAt())) {
            throw new GocastBindingConflictException("A newer TUM.Live approval has replaced this request");
        }
        return new GocastApprovalStartDTO(approval.approvalUrl(), approval.expiresAt());
    }

    /**
     * Revokes the exact remote grant before removing the saved binding.
     *
     * @param courseId the Artemis course identifier
     */
    public void unlink(long courseId) {
        var claim = connectionRepository.claimUnlink(courseId);
        if (claim.isEmpty()) {
            return;
        }
        BindingSnapshot binding = claim.get();
        connectorService.revokeGrant(binding.gocastCourseId(), binding.grantId());
        if (!connectionRepository.completeUnlink(binding)) {
            throw new GocastBindingConflictException("The TUM.Live connection changed while it was being disconnected");
        }
    }

    /**
     * Exchanges and saves a matching approval callback.
     *
     * @param requestId the remote request identifier
     * @param state     the opaque browser state
     * @param code      the single-use redeem code
     * @return the safe callback result
     */
    public GocastApprovalResultDTO completeApproval(String requestId, String state, String code) {
        String stateHash = hash(state);
        var claim = connectionRepository.claimAttempt(stateHash, requestId, clock.instant());
        if (claim.isEmpty()) {
            return new GocastApprovalResultDTO(false, null);
        }
        if (claim.get().getStatus() == GocastApprovalAttemptStatus.COMPLETED) {
            return connectionRepository.getBindingSnapshot(claim.get().getCourseId()).map(binding -> new GocastApprovalResultDTO(true, binding.courseId()))
                    .orElseGet(() -> new GocastApprovalResultDTO(false, null));
        }

        var verified = connectorService.redeemApproval(requestId, state, code);
        var binding = connectionRepository.completeAttempt(stateHash, requestId, verified, clock.instant());
        return new GocastApprovalResultDTO(true, binding.getCourseId());
    }

    private String randomOpaqueValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String state) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static GocastBindingDTO binding(BindingSnapshot binding, GocastBindingConnectionStatus status, boolean upstreamUnavailable) {
        return new GocastBindingDTO(true, status, binding.gocastCourseId(), binding.courseName(), binding.courseSlug(), binding.visibility(), null, upstreamUnavailable);
    }

    private static GocastBindingDTO unlinked() {
        return new GocastBindingDTO(true, GocastBindingConnectionStatus.UNLINKED, null, null, null, null, null, false);
    }
}
