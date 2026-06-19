package de.tum.cit.aet.artemis.videosource.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;

/**
 * Service for managing the lifecycle of {@link GocastCourseBinding} entities.
 * <p>
 * Handles creating, retrieving, updating and deleting gocast course bindings.
 * The full REST interface is exposed by {@code GocastIntegrationResource} (Task A4).
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class GocastBindingService {

    private final GocastCourseBindingRepository bindingRepository;

    public GocastBindingService(GocastCourseBindingRepository bindingRepository) {
        this.bindingRepository = bindingRepository;
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
     *
     * @param courseId         the Artemis course id
     * @param gocastCourseId   the numeric id of the gocast course
     * @param gocastCourseSlug the slug of the gocast course (used for watch-page links)
     * @return the persisted binding
     */
    public GocastCourseBinding createBinding(long courseId, long gocastCourseId, String gocastCourseSlug) {
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
     * Delete the binding for the given Artemis course, if one exists.
     *
     * @param courseId the Artemis course id
     */
    public void deleteBinding(long courseId) {
        bindingRepository.findByCourseId(courseId).ifPresent(bindingRepository::delete);
    }
}
