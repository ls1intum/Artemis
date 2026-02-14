package de.tum.cit.aet.artemis.core.security.policy.definitions;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.policy.AccessPolicy.when;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.hasStarted;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.isAdmin;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.memberOfGroup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;

/**
 * Policy definitions for course-level access control.
 */
@Configuration
@Profile(PROFILE_CORE)
public class CourseAccessPolicies {

    /**
     * Defines the course visibility policy.
     * <ul>
     * <li>Teaching assistants, editors, instructors, and admins can always see a course.</li>
     * <li>Students can see a course only if it has started (start date is null or in the past).</li>
     * <li>All other users are denied access by default.</li>
     * </ul>
     *
     * @return the course visibility access policy
     */
    @Bean
    public AccessPolicy<Course> courseVisibilityPolicy() {
        return AccessPolicy
                .forResource(Course.class).named("course-visibility").rule(when(memberOfGroup(Course::getTeachingAssistantGroupName).or(memberOfGroup(Course::getEditorGroupName))
                        .or(memberOfGroup(Course::getInstructorGroupName)).or(isAdmin())).thenAllow())
                .rule(when(memberOfGroup(Course::getStudentGroupName).and(hasStarted(Course::getStartDate))).thenAllow()).denyByDefault();
    }
}
