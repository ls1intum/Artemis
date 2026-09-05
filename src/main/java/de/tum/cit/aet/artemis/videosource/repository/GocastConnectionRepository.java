package de.tum.cit.aet.artemis.videosource.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.videosource.domain.GocastApprovalAttempt;
import de.tum.cit.aet.artemis.videosource.domain.GocastApprovalAttemptStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingConflictException;
import de.tum.cit.aet.artemis.videosource.service.GocastConnectorService.GrantStatus;

@Lazy
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
     * @param courseId  the Artemis course identifier
     * @param stateHash the server-side hash of the browser state
     * @param expiresAt the local attempt expiry
     * @return the saved attempt
     */
    public GocastApprovalAttempt startAttempt(long courseId, String stateHash, Instant expiresAt) {
        return transactionTemplate.execute(status -> {
            lockCourse(courseId);
            bindingRepository.findByCourseId(courseId).ifPresent(binding -> {
                if (binding.getStatus() == GocastBindingStatus.ACTIVE || binding.getStatus() == GocastBindingStatus.UNLINKING) {
                    throw conflict("This Artemis course is already connected to TUM.Live");
                }
                bindingRepository.delete(binding);
            });
            Instant now = Instant.now();
            GocastApprovalAttempt attempt = attemptRepository.findByCourseId(courseId).orElseGet(GocastApprovalAttempt::new);
            attempt.setCourseId(courseId);
            attempt.setStateHash(stateHash);
            attempt.setRequestId(null);
            attempt.setExpiresAt(expiresAt);
            attempt.setStatus(GocastApprovalAttemptStatus.PENDING);
            attempt.setCreatedAt(now);
            attempt.setUpdatedAt(now);
            return attemptRepository.saveAndFlush(attempt);
        });
    }

    /**
     * Attaches the remote request identifier if the pending attempt is still current.
     *
     * @param courseId  the Artemis course identifier
     * @param stateHash the expected state hash
     * @param requestId the remote request identifier
     * @param expiresAt the verified remote expiry
     * @return whether the pending attempt was still current
     */
    public boolean attachRemoteRequest(long courseId, String stateHash, String requestId, Instant expiresAt) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            lockCourse(courseId);
            Optional<GocastApprovalAttempt> attempt = attemptRepository.findByCourseId(courseId);
            if (attempt.isEmpty() || attempt.get().getStatus() != GocastApprovalAttemptStatus.PENDING || !attempt.get().getStateHash().equals(stateHash)
                    || attempt.get().getRequestId() != null) {
                return false;
            }
            attempt.get().setRequestId(requestId);
            attempt.get().setExpiresAt(expiresAt);
            attempt.get().setUpdatedAt(Instant.now());
            attemptRepository.save(attempt.get());
            return true;
        }));
    }

    /**
     * Claims a matching, usable approval attempt for the remote exchange.
     *
     * @param stateHash the saved state hash
     * @param requestId the expected remote request identifier
     * @param now       the current time used for the expiry check
     * @return the claimed attempt, or an empty result when it is not usable
     */
    public Optional<GocastApprovalAttempt> claimAttempt(String stateHash, String requestId, Instant now) {
        Optional<GocastApprovalAttempt> observed = attemptRepository.findByStateHash(stateHash);
        if (observed.isEmpty()) {
            return Optional.empty();
        }
        return transactionTemplate.execute(status -> {
            lockCourse(observed.get().getCourseId());
            Optional<GocastApprovalAttempt> current = attemptRepository.findByStateHash(stateHash);
            if (current.isEmpty() || !requestId.equals(current.get().getRequestId())) {
                return Optional.empty();
            }
            GocastApprovalAttempt attempt = current.get();
            if (!attempt.getExpiresAt().isAfter(now)) {
                if (attempt.getStatus() != GocastApprovalAttemptStatus.COMPLETED) {
                    attempt.setStatus(GocastApprovalAttemptStatus.EXPIRED);
                    attempt.setUpdatedAt(now);
                    attemptRepository.save(attempt);
                }
                return Optional.empty();
            }
            if (attempt.getStatus() == GocastApprovalAttemptStatus.COMPLETED) {
                return Optional.of(attempt);
            }
            if (attempt.getStatus() != GocastApprovalAttemptStatus.PENDING) {
                return Optional.empty();
            }
            attempt.setStatus(GocastApprovalAttemptStatus.CLAIMED);
            attempt.setUpdatedAt(now);
            return Optional.of(attemptRepository.save(attempt));
        });
    }

    /**
     * Saves a verified course binding if the claimed attempt is still current and usable.
     *
     * @param stateHash      the saved state hash
     * @param requestId      the verified remote request identifier
     * @param verifiedCourse the course and exact grant verified by GoCast
     * @param now            the current time used for the expiry check and timestamps
     * @return the saved binding
     */
    public GocastCourseBinding completeAttempt(String stateHash, String requestId, GocastVerifiedCourseDTO verifiedCourse, Instant now) {
        Optional<GocastApprovalAttempt> observed = attemptRepository.findByStateHash(stateHash);
        if (observed.isEmpty()) {
            throw conflict("The TUM.Live approval is no longer current");
        }
        try {
            return transactionTemplate.execute(status -> {
                long courseId = observed.get().getCourseId();
                lockCourse(courseId);
                GocastApprovalAttempt attempt = attemptRepository.findByStateHash(stateHash).orElseThrow(() -> conflict("The TUM.Live approval is no longer current"));
                if (!requestId.equals(attempt.getRequestId())) {
                    throw conflict("The TUM.Live approval is no longer current");
                }
                if (!attempt.getExpiresAt().isAfter(now)) {
                    attempt.setStatus(GocastApprovalAttemptStatus.EXPIRED);
                    attempt.setUpdatedAt(now);
                    attemptRepository.save(attempt);
                    throw conflict("The TUM.Live approval has expired");
                }
                if (attempt.getStatus() == GocastApprovalAttemptStatus.COMPLETED) {
                    return bindingRepository.findByCourseId(courseId).orElseThrow(() -> conflict("The TUM.Live approval result is no longer available"));
                }
                if (attempt.getStatus() != GocastApprovalAttemptStatus.CLAIMED) {
                    throw conflict("The TUM.Live approval cannot be completed");
                }
                if (bindingRepository.findByCourseId(courseId).isPresent()) {
                    throw conflict("This Artemis course is already connected to TUM.Live");
                }
                if (bindingRepository.findByGocastCourseId(verifiedCourse.courseId()).isPresent()) {
                    throw conflict("This TUM.Live course is already connected to another Artemis course");
                }

                GocastCourseBinding binding = new GocastCourseBinding();
                binding.setCourseId(courseId);
                binding.setGocastCourseId(verifiedCourse.courseId());
                binding.setGocastGrantId(verifiedCourse.grantId());
                binding.setCourseSlug(verifiedCourse.courseSlug());
                binding.setCourseName(verifiedCourse.courseName());
                binding.setVisibility(verifiedCourse.courseVisibility());
                binding.setStatus(GocastBindingStatus.ACTIVE);
                binding.setCreatedAt(now);
                binding.setUpdatedAt(now);
                binding = bindingRepository.saveAndFlush(binding);

                attempt.setStatus(GocastApprovalAttemptStatus.COMPLETED);
                attempt.setUpdatedAt(now);
                attemptRepository.save(attempt);
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
        return attemptRepository.findByCourseId(courseId).map(attempt -> new AttemptSnapshot(attempt.getCourseId(), attempt.getStateHash(), attempt.getRequestId(),
                attempt.getExpiresAt(), attempt.getStatus(), attempt.getVersion()));
    }

    /**
     * Persists a stable unlink claim for the exact saved grant.
     *
     * @param courseId the Artemis course identifier
     * @return the saved claim, or an empty result if no binding exists
     */
    public Optional<BindingSnapshot> claimUnlink(long courseId) {
        return transactionTemplate.execute(status -> {
            lockCourse(courseId);
            attemptRepository.findByCourseId(courseId).ifPresent(attemptRepository::delete);
            return bindingRepository.findByCourseId(courseId).map(binding -> {
                if (binding.getStatus() != GocastBindingStatus.UNLINKING) {
                    binding.setStatus(GocastBindingStatus.UNLINKING);
                    binding.setUpdatedAt(Instant.now());
                    binding = bindingRepository.saveAndFlush(binding);
                }
                return snapshot(binding);
            });
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
            if (current.isEmpty() || !matches(current.get(), claim)) {
                return false;
            }
            bindingRepository.delete(current.get());
            return true;
        }));
    }

    /**
     * Applies remote grant metadata only when the observed binding is still current.
     *
     * @param claim        the binding snapshot used for the remote request
     * @param remoteStatus the verified remote response
     * @return whether the matching binding was updated
     */
    public boolean updateGrantStatus(BindingSnapshot claim, GrantStatus remoteStatus) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            lockCourse(claim.courseId());
            Optional<GocastCourseBinding> current = bindingRepository.findByCourseId(claim.courseId());
            if (current.isEmpty() || !matches(current.get(), claim)) {
                return false;
            }
            GocastCourseBinding binding = current.get();
            if (binding.getStatus() == GocastBindingStatus.UNLINKING) {
                return false;
            }
            if (Boolean.FALSE.equals(remoteStatus.active())) {
                binding.setStatus(GocastBindingStatus.REVOKED);
            }
            else if (Boolean.TRUE.equals(remoteStatus.active())) {
                binding.setCourseSlug(remoteStatus.courseSlug());
                binding.setCourseName(remoteStatus.courseName());
                binding.setVisibility(remoteStatus.courseVisibility());
                binding.setStatus(GocastBindingStatus.ACTIVE);
            }
            else {
                return false;
            }
            binding.setUpdatedAt(Instant.now());
            bindingRepository.save(binding);
            return true;
        }));
    }

    private void lockCourse(long courseId) {
        courseRepository.findByIdWithPessimisticWrite(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    private static BindingSnapshot snapshot(GocastCourseBinding binding) {
        return new BindingSnapshot(binding.getCourseId(), binding.getGocastCourseId(), binding.getGocastGrantId(), binding.getVersion(), binding.getStatus(),
                binding.getCourseSlug(), binding.getCourseName(), binding.getVisibility());
    }

    private static boolean matches(GocastCourseBinding binding, BindingSnapshot claim) {
        return binding.getGocastCourseId() == claim.gocastCourseId() && binding.getGocastGrantId() == claim.grantId() && binding.getVersion() == claim.version();
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

    public record BindingSnapshot(long courseId, long gocastCourseId, long grantId, long version, GocastBindingStatus status, String courseSlug, String courseName,
            String visibility) {
    }

    public record AttemptSnapshot(long courseId, String stateHash, String requestId, Instant expiresAt, GocastApprovalAttemptStatus status, long version) {
    }
}
