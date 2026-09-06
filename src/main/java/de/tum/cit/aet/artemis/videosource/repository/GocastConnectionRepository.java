package de.tum.cit.aet.artemis.videosource.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.videosource.domain.GocastApprovalAttempt;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingConflictException;
import de.tum.cit.aet.artemis.videosource.service.GocastConnectorService.GrantDetails;

@Lazy
@Profile(PROFILE_CORE)
@Repository
public class GocastConnectionRepository {

    private final TransactionTemplate transactionTemplate;

    private final CourseRepository courseRepository;

    private final GocastCourseBindingRepository bindingRepository;

    private final GocastApprovalAttemptRepository attemptRepository;

    public GocastConnectionRepository(PlatformTransactionManager transactionManager, CourseRepository courseRepository, GocastCourseBindingRepository bindingRepository,
            GocastApprovalAttemptRepository attemptRepository) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.courseRepository = courseRepository;
        this.bindingRepository = bindingRepository;
        this.attemptRepository = attemptRepository;
    }

    /**
     * Replaces the current approval attempt while holding the course row lock.
     *
     * @param courseId      the Artemis course identifier
     * @param stateHash     the server-side hash of the browser state
     * @param integrationId the GoCast integration identity authenticated at start
     * @param expiresAt     the local attempt expiry
     * @return the saved attempt
     */
    public GocastApprovalAttempt startAttempt(long courseId, String stateHash, long integrationId, Instant expiresAt) {
        return transactionTemplate.execute(status -> {
            lockCourse(courseId);
            bindingRepository.findByCourseId(courseId).ifPresent(binding -> {
                if (binding.getStatus() == GocastBindingStatus.ACTIVE) {
                    throw conflict("This Artemis course is already connected to TUM.Live");
                }
                bindingRepository.delete(binding);
            });
            GocastApprovalAttempt attempt = attemptRepository.findByCourseId(courseId).orElseGet(GocastApprovalAttempt::new);
            attempt.setCourseId(courseId);
            attempt.setStateHash(stateHash);
            attempt.setIntegrationId(integrationId);
            attempt.setExpiresAt(expiresAt);
            return attemptRepository.saveAndFlush(attempt);
        });
    }

    /**
     * Finds a matching, unexpired approval attempt without mutating it.
     *
     * @param stateHash the saved state hash
     * @param now       the current time used for the expiry check
     * @return the usable attempt, or an empty result when it is not current
     */
    public Optional<AttemptClaim> findUsableAttempt(String stateHash, Instant now) {
        return attemptRepository.findByStateHash(stateHash).filter(attempt -> attempt.getExpiresAt().isAfter(now))
                .map(attempt -> new AttemptClaim(attempt.getCourseId(), attempt.getIntegrationId(), attempt.getExpiresAt()));
    }

    /**
     * Saves a verified course binding if the pending attempt is still current and usable.
     *
     * @param stateHash      the saved state hash
     * @param verifiedCourse the course and exact grant verified by GoCast
     * @param now            the current time used for the expiry check
     * @return the saved binding
     */
    public GocastCourseBinding completeAttempt(String stateHash, GocastVerifiedCourseDTO verifiedCourse, Instant now) {
        Optional<GocastApprovalAttempt> observed = attemptRepository.findByStateHash(stateHash);
        if (observed.isEmpty()) {
            throw conflict("The TUM.Live approval is no longer current");
        }
        try {
            return transactionTemplate.execute(status -> {
                long courseId = observed.get().getCourseId();
                lockCourse(courseId);
                GocastApprovalAttempt attempt = attemptRepository.findByStateHash(stateHash).orElseThrow(() -> conflict("The TUM.Live approval is no longer current"));
                if (attempt.getIntegrationId() != verifiedCourse.integrationId()) {
                    throw conflict("The TUM.Live approval is no longer current");
                }
                if (!attempt.getExpiresAt().isAfter(now)) {
                    throw conflict("The TUM.Live approval has expired");
                }
                if (bindingRepository.findByCourseId(courseId).isPresent()) {
                    throw conflict("This Artemis course is already connected to TUM.Live");
                }
                if (bindingRepository.findByGocastCourseId(verifiedCourse.courseId()).isPresent()) {
                    throw conflict("This TUM.Live course is already connected to another Artemis course");
                }

                GocastCourseBinding binding = new GocastCourseBinding();
                binding.setCourseId(courseId);
                binding.setIntegrationId(verifiedCourse.integrationId());
                binding.setGocastCourseId(verifiedCourse.courseId());
                binding.setGocastGrantId(verifiedCourse.grantId());
                binding.setCourseSlug(verifiedCourse.courseSlug());
                binding.setCourseName(verifiedCourse.courseName());
                binding.setVisibility(verifiedCourse.courseVisibility());
                binding.setStatus(GocastBindingStatus.ACTIVE);
                binding = bindingRepository.saveAndFlush(binding);

                attemptRepository.delete(attempt);
                return binding;
            });
        }
        catch (DataIntegrityViolationException exception) {
            if (isRemoteCourseUniqueViolation(exception)) {
                throw conflict("This TUM.Live course is already connected to another Artemis course", exception);
            }
            throw exception;
        }
    }

    public Optional<BindingSnapshot> getBindingSnapshot(long courseId) {
        return bindingRepository.findByCourseId(courseId).map(GocastConnectionRepository::snapshot);
    }

    public Optional<AttemptSnapshot> getAttemptSnapshot(long courseId) {
        return attemptRepository.findByCourseId(courseId).map(attempt -> new AttemptSnapshot(attempt.getExpiresAt()));
    }

    /**
     * Cancels any pending approval and reads the exact saved grant under the course lock.
     *
     * @param courseId the Artemis course identifier
     * @return the saved binding, or an empty result if no binding exists
     */
    public Optional<BindingSnapshot> prepareUnlink(long courseId) {
        return transactionTemplate.execute(status -> {
            lockCourse(courseId);
            attemptRepository.findByCourseId(courseId).ifPresent(attemptRepository::delete);
            return bindingRepository.findByCourseId(courseId).map(GocastConnectionRepository::snapshot);
        });
    }

    /**
     * Removes a pending approval only when it is still the attempt created by the caller.
     *
     * @param stateHash the exact attempt state hash
     */
    public void cancelAttempt(String stateHash) {
        Optional<GocastApprovalAttempt> observed = attemptRepository.findByStateHash(stateHash);
        if (observed.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            lockCourse(observed.get().getCourseId());
            attemptRepository.findByCourseId(observed.get().getCourseId()).filter(attempt -> stateHash.equals(attempt.getStateHash())).ifPresent(attemptRepository::delete);
        });
    }

    /**
     * Removes a binding only when it still matches the completed remote revoke.
     *
     * @param claim the exact binding that was revoked remotely
     * @return whether the matching binding was removed
     */
    public boolean completeUnlink(BindingSnapshot claim) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            lockCourse(claim.courseId());
            Optional<GocastCourseBinding> current = bindingRepository.findByCourseId(claim.courseId());
            if (current.isEmpty()) {
                return true;
            }
            if (!sameGrant(current.get(), claim)) {
                return false;
            }
            bindingRepository.delete(current.get());
            return true;
        }));
    }

    /**
     * Applies remote grant metadata only when the observed binding is still current.
     *
     * @param claim       the binding snapshot used for the remote request
     * @param remoteGrant the verified remote response
     * @return whether the matching binding was updated
     */
    public boolean updateGrantMetadata(BindingSnapshot claim, GrantDetails remoteGrant) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            lockCourse(claim.courseId());
            Optional<GocastCourseBinding> current = bindingRepository.findByCourseId(claim.courseId());
            if (current.isEmpty() || !matchesCurrentSnapshot(current.get(), claim)) {
                return false;
            }
            GocastCourseBinding binding = current.get();
            if (binding.getStatus() != GocastBindingStatus.ACTIVE) {
                return false;
            }
            if (sameMetadata(binding, remoteGrant)) {
                return true;
            }
            binding.setCourseSlug(remoteGrant.courseSlug());
            binding.setCourseName(remoteGrant.courseName());
            binding.setVisibility(remoteGrant.courseVisibility());
            bindingRepository.save(binding);
            return true;
        }));
    }

    /**
     * Marks the exact observed binding revoked after GoCast reports that the grant does not exist.
     *
     * @param claim the binding used for the remote request
     * @return whether the matching binding was marked revoked
     */
    public boolean markGrantRevoked(BindingSnapshot claim) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            lockCourse(claim.courseId());
            Optional<GocastCourseBinding> current = bindingRepository.findByCourseId(claim.courseId());
            if (current.isEmpty() || !matchesCurrentSnapshot(current.get(), claim)) {
                return false;
            }
            if (current.get().getStatus() != GocastBindingStatus.REVOKED) {
                current.get().setStatus(GocastBindingStatus.REVOKED);
                bindingRepository.save(current.get());
            }
            return true;
        }));
    }

    private void lockCourse(long courseId) {
        courseRepository.findByIdWithPessimisticWrite(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    private static BindingSnapshot snapshot(GocastCourseBinding binding) {
        return new BindingSnapshot(binding.getCourseId(), binding.getIntegrationId(), binding.getGocastCourseId(), binding.getGocastGrantId(), binding.getVersion(),
                binding.getStatus(), binding.getCourseSlug(), binding.getCourseName(), binding.getVisibility());
    }

    private static boolean matchesCurrentSnapshot(GocastCourseBinding binding, BindingSnapshot claim) {
        return sameGrant(binding, claim) && binding.getVersion() == claim.version();
    }

    private static boolean sameGrant(GocastCourseBinding binding, BindingSnapshot claim) {
        return binding.getIntegrationId() == claim.integrationId() && binding.getGocastCourseId() == claim.gocastCourseId() && binding.getGocastGrantId() == claim.grantId();
    }

    private static boolean sameMetadata(GocastCourseBinding binding, GrantDetails remoteGrant) {
        return Objects.equals(binding.getCourseSlug(), remoteGrant.courseSlug()) && Objects.equals(binding.getCourseName(), remoteGrant.courseName())
                && Objects.equals(binding.getVisibility(), remoteGrant.courseVisibility());
    }

    private static GocastBindingConflictException conflict(String message) {
        return new GocastBindingConflictException(message);
    }

    private static GocastBindingConflictException conflict(String message, Throwable cause) {
        return new GocastBindingConflictException(message, cause);
    }

    private static boolean isRemoteCourseUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("ux_gocast_binding_remote_course")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record BindingSnapshot(long courseId, long integrationId, long gocastCourseId, long grantId, long version, GocastBindingStatus status, String courseSlug,
            String courseName, String visibility) {
    }

    public record AttemptSnapshot(Instant expiresAt) {
    }

    public record AttemptClaim(long courseId, long integrationId, Instant expiresAt) {
    }
}
