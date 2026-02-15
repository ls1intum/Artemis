package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.PolicyEngine;

/**
 * Service for determining whether a course is visible to a user.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseVisibleService {

    private final PolicyEngine policyEngine;

    private final AccessPolicy<Course> courseVisibilityPolicy;

    public CourseVisibleService(PolicyEngine policyEngine, @Qualifier("courseVisibilityPolicy") AccessPolicy<Course> courseVisibilityPolicy) {
        this.policyEngine = policyEngine;
        this.courseVisibilityPolicy = courseVisibilityPolicy;
    }

    /**
     * Checks if a course is visible for a user based on their role and the course's start date.
     *
     * @param user   the user for whom to check visibility
     * @param course the course to check visibility for
     * @return true if the course is visible for the user, false otherwise
     */
    public boolean isCourseVisibleForUser(User user, Course course) {
        return policyEngine.isAllowed(courseVisibilityPolicy, user, course);
    }
}
