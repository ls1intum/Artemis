package de.tum.cit.aet.artemis.videosource.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;

/**
 * Spring Data JPA repository for {@link GocastCourseBinding}.
 */
@Profile(PROFILE_CORE)
@Repository
@Lazy
public interface GocastCourseBindingRepository extends ArtemisJpaRepository<GocastCourseBinding, Long> {

    /**
     * Find the binding for the given Artemis course id.
     *
     * @param courseId the id of the Artemis course
     * @return an optional containing the binding, or empty if none exists
     */
    Optional<GocastCourseBinding> findByCourseId(long courseId);

    /**
     * Find the binding for the given Artemis course id, or throw {@link EntityNotFoundException}.
     *
     * @param courseId the id of the Artemis course
     * @return the binding for the given course
     * @throws EntityNotFoundException if no binding exists for the course
     */
    default GocastCourseBinding findByCourseIdElseThrow(long courseId) {
        return getValueElseThrow(findByCourseId(courseId));
    }
}
