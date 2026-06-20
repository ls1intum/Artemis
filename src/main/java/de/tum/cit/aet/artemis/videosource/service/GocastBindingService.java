package de.tum.cit.aet.artemis.videosource.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.dto.GocastCourseDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;

/**
 * Service for managing the lifecycle of {@link GocastCourseBinding} entities.
 * <p>
 * Handles creating, retrieving, updating and deleting gocast course bindings, as well as the
 * server-to-server binding-status refresh (EP7) that drives the {@code PENDING → ACTIVE/REVOKED}
 * state machine. The full REST interface is exposed by {@code GocastIntegrationResource} (Task A4).
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class GocastBindingService {

    private static final Logger log = LoggerFactory.getLogger(GocastBindingService.class);

    private final GocastCourseBindingRepository bindingRepository;

    private final GocastConnectorService connectorService;

    public GocastBindingService(GocastCourseBindingRepository bindingRepository, Optional<GocastConnectorService> connectorService) {
        this.bindingRepository = bindingRepository;
        // The connector is only present when the gocast integration is fully configured (see GocastEnabled).
        // It is required for refreshStatusFromUpstream; the other operations work without it.
        this.connectorService = connectorService.orElse(null);
    }

    /**
     * Find the binding for the given Artemis course, or throw if none exists.
     *
     * @param courseId the id of the Artemis course
     * @return the binding
     * @throws de.tum.cit.aet.artemis.core.exception.EntityNotFoundException if no binding exists
     */
    public GocastCourseBinding getBindingByCourseIdElseThrow(long courseId) {
        return bindingRepository.findByCourseIdElseThrow(courseId);
    }

    /**
     * Find the binding for the given Artemis course, returning empty if none exists.
     *
     * @param courseId the id of the Artemis course
     * @return an optional containing the binding
     */
    public Optional<GocastCourseBinding> findBindingByCourseId(long courseId) {
        return bindingRepository.findByCourseId(courseId);
    }

    /**
     * Create and persist a new {@link GocastCourseBinding} for the given Artemis course.
     * <p>
     * Verifies via EP1 that the requesting instructor actually administers the given gocast course
     * before creating the binding (IDOR guard). If the instructor does not administer the course,
     * an {@link AccessForbiddenException} is thrown.
     * <p>
     * If an existing {@code REVOKED} binding exists for the course, it is reset to {@code PENDING}
     * rather than creating a new row (which would violate the unique constraint on {@code course_id}).
     * If a {@code PENDING} or {@code ACTIVE} binding already exists, an
     * {@link IllegalStateException} is thrown (the caller maps this to 409 CONFLICT).
     *
     * @param courseId         the Artemis course id
     * @param gocastCourseId   the numeric id of the gocast course
     * @param gocastCourseSlug the slug of the gocast course (used for watch-page links)
     * @param instructorLrzId  the LRZ ID of the instructor; verified against EP1
     * @return the persisted (new or reset) binding
     * @throws AccessForbiddenException if the instructor does not administer the gocast course
     * @throws IllegalStateException    if a non-REVOKED binding already exists for the course
     */
    public GocastCourseBinding createBinding(long courseId, long gocastCourseId, String gocastCourseSlug, String instructorLrzId) {
        if (connectorService == null) {
            throw new IllegalStateException("Gocast connector is not available; cannot verify course ownership");
        }

        // IDOR guard: verify the instructor administers the requested gocast course via EP1.
        List<GocastCourseDTO> administeredCourses;
        try {
            administeredCourses = connectorService.listAdministeredCourses(instructorLrzId, 0, "");
        }
        catch (GocastIntegrationException ex) {
            log.warn("EP1 listAdministeredCourses failed for instructor {}: {}", instructorLrzId, ex.getMessage());
            throw ex;
        }
        boolean isAdministered = administeredCourses.stream().anyMatch(c -> c.id() == gocastCourseId && gocastCourseSlug.equals(c.slug()));
        if (!isAdministered) {
            log.warn("Instructor {} attempted to bind gocast course {} but does not administer it", instructorLrzId, gocastCourseId);
            throw new AccessForbiddenException("Instructor does not administer the requested gocast course");
        }

        // MEDIUM-2: handle existing binding.
        Optional<GocastCourseBinding> existing = bindingRepository.findByCourseId(courseId);
        if (existing.isPresent()) {
            GocastCourseBinding existingBinding = existing.get();
            if (existingBinding.getStatus() == GocastBindingStatus.REVOKED) {
                // Reset the REVOKED binding to PENDING so we don't violate the unique constraint.
                log.info("Resetting REVOKED binding for Artemis course {} to PENDING (new gocast course: {})", courseId, gocastCourseId);
                existingBinding.setGocastCourseId(gocastCourseId);
                existingBinding.setGocastCourseSlug(gocastCourseSlug);
                existingBinding.setStatus(GocastBindingStatus.PENDING);
                return bindingRepository.save(existingBinding);
            }
            else {
                throw new IllegalStateException("A " + existingBinding.getStatus() + " binding already exists for course " + courseId);
            }
        }

        GocastCourseBinding binding = new GocastCourseBinding();
        binding.setCourseId(courseId);
        binding.setGocastCourseId(gocastCourseId);
        binding.setGocastCourseSlug(gocastCourseSlug);
        binding.setStatus(GocastBindingStatus.PENDING);
        return bindingRepository.save(binding);
    }

    /**
     * Update the status of an existing binding and persist the change.
     *
     * @param binding the binding to update
     * @param status  the new status
     * @return the updated binding
     */
    public GocastCourseBinding updateStatus(GocastCourseBinding binding, GocastBindingStatus status) {
        binding.setStatus(status);
        return bindingRepository.save(binding);
    }

    /**
     * Refresh the status of a {@code PENDING} binding from gocast via EP7 (server-to-server confirmation).
     * <p>
     * This is the authoritative binding-status transition; it never trusts the browser redirect alone:
     * <ul>
     * <li>If the binding is not {@code PENDING} (already {@code ACTIVE} or {@code REVOKED}), EP7 is
     * <em>not</em> called and the binding is returned unchanged.</li>
     * <li>If EP7 ({@code getBindingStatus}) returns {@code true}, the binding is flipped to
     * {@code ACTIVE} and persisted.</li>
     * <li>If EP7 returns {@code false}, the binding stays {@code PENDING} (approval not yet completed;
     * the instructor must still grant service-account access on the gocast side).</li>
     * <li>If EP7 <em>throws</em> any exception (including {@code 403} / {@code 5xx} / transport errors),
     * the binding is left unchanged and stays {@code PENDING}. A thrown exception is not a definitive
     * "unbound" signal — it can indicate a transient auth/config failure. Only an explicit
     * {@code false} return from EP7 means approval has not completed.
     * {@code REVOKED} is only reached from an {@code ACTIVE} binding via the management-call paths
     * (EP8/EP2), never from this PENDING-refresh path.</li>
     * </ul>
     *
     * @param binding the binding to refresh
     * @return the (possibly updated) binding
     */
    public GocastCourseBinding refreshStatusFromUpstream(GocastCourseBinding binding) {
        if (binding.getStatus() != GocastBindingStatus.PENDING) {
            // Only PENDING bindings are confirmed via EP7; ACTIVE/REVOKED are terminal here.
            return binding;
        }
        if (connectorService == null) {
            // Defensive: the resource that reaches this method is itself gated on GocastEnabled, so the
            // connector is present in practice. Guard anyway to avoid an NPE if wiring ever changes.
            log.warn("Cannot refresh binding status for course {}: gocast connector is not available", binding.getCourseId());
            return binding;
        }
        try {
            boolean bound = connectorService.getBindingStatus(binding.getGocastCourseId());
            if (bound) {
                log.info("Binding for Artemis course {} flipped to ACTIVE (EP7 confirmed)", binding.getCourseId());
                return updateStatus(binding, GocastBindingStatus.ACTIVE);
            }
            // EP7 returned explicit false: approval not yet completed — leave PENDING.
            return binding;
        }
        catch (GocastIntegrationException ex) {
            // Any thrown exception (403, 5xx, transport) is not a definitive revocation signal for a
            // PENDING binding. Leave the binding unchanged so a transient auth/config/outage failure
            // does not corrupt the binding state. The instructor can retry.
            log.warn("EP7 getBindingStatus failed for gocast course {}: status={} — leaving PENDING binding unchanged", binding.getGocastCourseId(), ex.getUpstreamStatus());
            return binding;
        }
    }

    /**
     * Delete the binding for the given Artemis course.
     * <p>
     * The lookup-and-delete happens in a single path so the controller does not need a separate
     * existence check (which would be a second round-trip and a concurrent-delete race).
     *
     * @param courseId the Artemis course id
     * @throws de.tum.cit.aet.artemis.core.exception.EntityNotFoundException if no binding exists for the course
     */
    public void deleteBinding(long courseId) {
        GocastCourseBinding binding = bindingRepository.findByCourseIdElseThrow(courseId);
        bindingRepository.delete(binding);
    }
}
