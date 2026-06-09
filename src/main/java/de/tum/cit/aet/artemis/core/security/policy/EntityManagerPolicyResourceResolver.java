package de.tum.cit.aet.artemis.core.security.policy;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

/**
 * Default {@link PolicyResourceResolver} for {@link Course} entities.
 * <p>
 * Loads a course by ID using the {@link CourseRepository}. This resolver is used
 * by the {@link de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy.EnforceAccessPolicyAspect}
 * to load the resource entity for policy evaluation on course-level endpoints.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy
public class EntityManagerPolicyResourceResolver implements PolicyResourceResolver<Course> {

    private final CourseRepository courseRepository;

    public EntityManagerPolicyResourceResolver(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public Class<Course> getResourceType() {
        return Course.class;
    }

    @Override
    public Course loadById(long id) {
        return courseRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Course", id));
    }
}
