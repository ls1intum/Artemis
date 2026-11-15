package de.tum.cit.aet.artemis.videosource.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingConflictException;
import de.tum.cit.aet.artemis.videosource.service.GocastConnectorService.GrantDetails;

@Lazy
@Profile(PROFILE_CORE)
@Repository
public class GocastConnectionRepository {

    private final CourseRepository courseRepository;

    private final GocastCourseBindingRepository bindingRepository;

    public GocastConnectionRepository(CourseRepository courseRepository, GocastCourseBindingRepository bindingRepository) {
        this.courseRepository = courseRepository;
        this.bindingRepository = bindingRepository;
    }

    /**
     * Replaces the pending approval without overwriting an active connection.
     *
     * @param courseId      the Artemis course
     * @param stateHash     the new state hash
     * @param integrationId the authenticated integration
     * @param expiresAt     the approval expiry
     * @return the pending connection row
     */
    public GocastCourseBinding startAttempt(long courseId, String stateHash, long integrationId, Instant expiresAt) {
        if (!courseRepository.existsById(courseId)) {
            throw new EntityNotFoundException("Course", courseId);
        }
        if (bindingRepository.replacePending(courseId, stateHash, integrationId, expiresAt) == 1) {
            return pendingOrConflict(courseId);
        }
        if (bindingRepository.findByCourseId(courseId).isPresent()) {
            throw conflict("This Artemis course is already connected to TUM.Live");
        }
        GocastCourseBinding pending = new GocastCourseBinding();
        pending.setCourseId(courseId);
        pending.setIntegrationId(integrationId);
        pending.setStatus(GocastBindingStatus.PENDING);
        pending.setStateHash(stateHash);
        pending.setExpiresAt(expiresAt);
        try {
            // Null generated ID and wrapper version force INSERT, never a merge over a concurrent active row.
            return bindingRepository.saveAndFlush(pending);
        }
        catch (DataIntegrityViolationException exception) {
            if (bindingRepository.replacePending(courseId, stateHash, integrationId, expiresAt) == 1) {
                return pendingOrConflict(courseId);
            }
            if (bindingRepository.findByCourseId(courseId).isPresent()) {
                throw conflict("This Artemis course is already connected to TUM.Live");
            }
            throw exception;
        }
    }

    private GocastCourseBinding pendingOrConflict(long courseId) {
        return bindingRepository.findPendingByCourseId(courseId).orElseThrow(() -> conflict("The TUM.Live approval is no longer current"));
    }

    /**
     * Reads an unexpired pending approval without changing it.
     *
     * @param stateHash the approval state hash
     * @param now       the current time
     * @return the matching approval claim
     */
    public Optional<AttemptClaim> findUsableAttempt(String stateHash, Instant now) {
        return bindingRepository.findPendingByStateHash(stateHash).filter(attempt -> attempt.getExpiresAt().isAfter(now))
                .map(attempt -> new AttemptClaim(attempt.getCourseId(), attempt.getIntegrationId(), attempt.getExpiresAt()));
    }

    /**
     * Converts only the exact usable approval into an active connection, in one guarded update.
     *
     * @param stateHash      the approval state hash
     * @param verifiedCourse the course and grant verified by GoCast
     * @param now            the current time
     * @return the completed connection
     */
    public GocastCourseBinding completeAttempt(String stateHash, GocastVerifiedCourseDTO verifiedCourse, Instant now) {
        var observed = bindingRepository.findPendingByStateHash(stateHash).orElseThrow(() -> conflict("The TUM.Live approval is no longer current"));
        try {
            if (bindingRepository.completePending(stateHash, verifiedCourse.integrationId(), now, verifiedCourse.courseId(), verifiedCourse.grantId(), verifiedCourse.courseSlug(),
                    verifiedCourse.courseName(), verifiedCourse.courseVisibility()) != 1) {
                throw conflict("The TUM.Live approval is no longer current or has expired");
            }
        }
        catch (DataIntegrityViolationException exception) {
            if (bindingRepository.findByGocastCourseId(verifiedCourse.courseId()).isPresent()) {
                throw conflict("This TUM.Live course is already connected to another Artemis course");
            }
            throw exception;
        }
        return bindingRepository.findByCourseId(observed.getCourseId())
                .filter(binding -> binding.getStatus() == GocastBindingStatus.ACTIVE && binding.getIntegrationId() == verifiedCourse.integrationId()
                        && binding.getGocastGrantId() == verifiedCourse.grantId() && binding.getGocastCourseId() == verifiedCourse.courseId())
                .orElseThrow(() -> conflict("The TUM.Live connection changed while approval was being completed"));
    }

    public Optional<BindingSnapshot> getBindingSnapshot(long courseId) {
        return bindingRepository.findByCourseId(courseId).map(GocastConnectionRepository::snapshot);
    }

    public Optional<AttemptSnapshot> getAttemptSnapshot(long courseId) {
        return bindingRepository.findPendingByCourseId(courseId).map(attempt -> new AttemptSnapshot(attempt.getExpiresAt()));
    }

    /**
     * Cancels a pending approval, then reads any completed connection for remote revocation.
     *
     * @param courseId the Artemis course
     * @return the saved grant, if present
     */
    public Optional<BindingSnapshot> prepareUnlink(long courseId) {
        bindingRepository.deletePendingByCourseId(courseId);
        return getBindingSnapshot(courseId);
    }

    /**
     * Cancels only the pending approval with this state hash.
     *
     * @param stateHash the exact approval state hash
     */
    public void cancelAttempt(String stateHash) {
        bindingRepository.deletePendingByStateHash(stateHash);
    }

    /**
     * Removes only the exact grant revoked remotely. Metadata refresh does not prevent disconnection.
     *
     * @param claim the revoked grant snapshot
     * @return whether the connection was removed or already absent
     */
    public boolean completeUnlink(BindingSnapshot claim) {
        if (bindingRepository.deleteExactGrant(claim.courseId(), claim.integrationId(), claim.gocastCourseId(), claim.grantId()) == 1) {
            return true;
        }
        return bindingRepository.findConnectionRowByCourseId(claim.courseId()).isEmpty();
    }

    /**
     * Refreshes metadata only if the observed active connection is still current.
     *
     * @param claim       the observed connection
     * @param remoteGrant the verified remote response
     * @return whether the observed connection remains current
     */
    public boolean updateGrantMetadata(BindingSnapshot claim, GrantDetails remoteGrant) {
        if (claim.status() != GocastBindingStatus.ACTIVE) {
            return false;
        }
        if (Objects.equals(claim.courseSlug(), remoteGrant.courseSlug()) && Objects.equals(claim.courseName(), remoteGrant.courseName())
                && Objects.equals(claim.visibility(), remoteGrant.courseVisibility())) {
            return getBindingSnapshot(claim.courseId()).filter(claim::equals).isPresent();
        }
        return bindingRepository.updateExactGrantMetadata(claim.courseId(), claim.integrationId(), claim.gocastCourseId(), claim.grantId(), claim.version(),
                remoteGrant.courseSlug(), remoteGrant.courseName(), remoteGrant.courseVisibility()) == 1;
    }

    /**
     * Marks only the exact observed connection revoked after GoCast reports that its grant is absent.
     *
     * @param claim the observed connection
     * @return whether the matching connection was marked revoked
     */
    public boolean markGrantRevoked(BindingSnapshot claim) {
        return bindingRepository.markExactGrantRevoked(claim.courseId(), claim.integrationId(), claim.gocastCourseId(), claim.grantId(), claim.version()) == 1;
    }

    private static BindingSnapshot snapshot(GocastCourseBinding binding) {
        return new BindingSnapshot(binding.getCourseId(), binding.getIntegrationId(), binding.getGocastCourseId(), binding.getGocastGrantId(), binding.getVersion(),
                binding.getStatus(), binding.getCourseSlug(), binding.getCourseName(), binding.getVisibility());
    }

    private static GocastBindingConflictException conflict(String message) {
        return new GocastBindingConflictException(message);
    }

    public record BindingSnapshot(long courseId, long integrationId, long gocastCourseId, long grantId, long version, GocastBindingStatus status, String courseSlug,
            String courseName, String visibility) {
    }

    public record AttemptSnapshot(Instant expiresAt) {
    }

    public record AttemptClaim(long courseId, long integrationId, Instant expiresAt) {
    }
}
