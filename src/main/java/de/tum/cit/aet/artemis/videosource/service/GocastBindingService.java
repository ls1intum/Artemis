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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.videosource.config.GocastConfiguration.GocastSettings;
import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
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

    private final GocastConnectorService connectorService;

    private final String callbackUrl;

    private final Clock clock;

    private final SecureRandom secureRandom;

    @Autowired
    public GocastBindingService(GocastConnectionRepository connectionRepository, GocastConnectorService connectorService, GocastSettings settings) {
        this(connectionRepository, connectorService, settings.callbackUri().toString(), Clock.systemUTC(), new SecureRandom());
    }

    GocastBindingService(GocastConnectionRepository connectionRepository, GocastConnectorService connectorService, String callbackUrl, Clock clock, SecureRandom secureRandom) {
        this.connectionRepository = connectionRepository;
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
            if (snapshot.status() == GocastBindingStatus.REVOKED) {
                return binding(snapshot, GocastBindingConnectionStatus.REVOKED, false);
            }
            try {
                var remoteGrant = connectorService.getGrant(snapshot.grantId());
                if (remoteGrant.courseId() != snapshot.gocastCourseId()) {
                    throw new GocastIntegrationException("TUM.Live grant does not match the saved course", HttpStatus.BAD_GATEWAY);
                }
                if (!connectionRepository.updateGrantMetadata(snapshot, remoteGrant)) {
                    return currentLocalState(courseId, null);
                }
                return new GocastBindingDTO(true, GocastBindingConnectionStatus.ACTIVE, remoteGrant.courseId(), remoteGrant.courseName(), remoteGrant.courseSlug(),
                        remoteGrant.courseVisibility(), null, false);
            }
            catch (GocastIntegrationException exception) {
                if (exception.getUpstreamStatus().equals(HttpStatus.NOT_FOUND)) {
                    if (!connectionRepository.markGrantRevoked(snapshot)) {
                        return currentLocalState(courseId, null);
                    }
                    return binding(snapshot, GocastBindingConnectionStatus.REVOKED, false);
                }
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
        GocastBindingConnectionStatus status = current.expiresAt().isAfter(clock.instant()) ? GocastBindingConnectionStatus.PENDING : GocastBindingConnectionStatus.EXPIRED;
        return new GocastBindingDTO(true, status, null, null, null, null, current.expiresAt(), false);
    }

    /**
     * Starts a short-lived approval and returns its validated GoCast URL.
     *
     * @param courseId the Artemis course identifier
     * @return the approval URL and expiry
     */
    public GocastApprovalStartDTO startApproval(long courseId) {
        var integration = connectorService.getIntegration();
        if (!callbackUrl.equals(integration.returnUrl())) {
            throw new GocastIntegrationException("The registered TUM.Live return URL does not match this Artemis instance", HttpStatus.BAD_GATEWAY);
        }
        String state = randomOpaqueValue();
        String stateHash = hash(state);
        var expiresAt = clock.instant().plus(APPROVAL_LIFETIME);
        connectionRepository.startAttempt(courseId, stateHash, integration.id(), expiresAt);
        return new GocastApprovalStartDTO(connectorService.authorizationUrl(integration.id(), state), expiresAt);
    }

    /**
     * Revokes the exact remote grant before removing the saved binding.
     *
     * @param courseId the Artemis course identifier
     */
    public void unlink(long courseId) {
        var saved = connectionRepository.prepareUnlink(courseId);
        if (saved.isEmpty()) {
            return;
        }
        BindingSnapshot binding = saved.get();
        connectorService.revokeGrant(binding.grantId());
        if (!connectionRepository.completeUnlink(binding)) {
            throw new GocastBindingConflictException("The TUM.Live connection changed while it was being disconnected");
        }
    }

    /**
     * Exchanges and saves a matching approval callback.
     *
     * @param state the opaque browser state
     * @param code  the single-use redeem code
     * @return the safe callback result
     */
    public GocastApprovalResultDTO completeApproval(String state, String code) {
        if (!GocastConnectorService.isOpaque(state)) {
            return new GocastApprovalResultDTO(false, null);
        }
        String stateHash = hash(state);
        var pending = connectionRepository.findUsableAttempt(stateHash, clock.instant());
        if (pending.isEmpty()) {
            return new GocastApprovalResultDTO(false, null);
        }

        var verified = connectorService.redeemApproval(pending.get().integrationId(), state, code);
        var binding = connectionRepository.completeAttempt(stateHash, verified, clock.instant());
        return new GocastApprovalResultDTO(true, binding.getCourseId());
    }

    /**
     * Cancels only the local pending attempt selected by an explicit GoCast denial.
     *
     * @param state the opaque browser state
     */
    public void cancelApproval(String state) {
        if (GocastConnectorService.isOpaque(state)) {
            connectionRepository.cancelAttempt(hash(state));
        }
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
