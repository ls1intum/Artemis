package de.tum.cit.aet.artemis.core.security.policy.definitions;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.EDITOR;
import static de.tum.cit.aet.artemis.core.security.Role.INSTRUCTOR;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static de.tum.cit.aet.artemis.core.security.Role.SUPER_ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.TEACHING_ASSISTANT;
import static de.tum.cit.aet.artemis.core.security.policy.AccessPolicy.when;
import static de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions.hasStarted;
import static de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions.isAdmin;
import static de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions.memberOfGroup;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Course_;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.PolicyProvider;

/**
 * Policy provider for course visibility access control.
 * <p>
 * Defines the course visibility policy:
 * <ul>
 * <li>Teaching assistants, editors, instructors, and admins can always see a course.</li>
 * <li>Students can see a course only if it has started (start date is null or in the past).</li>
 * <li>All other users are denied access by default.</li>
 * </ul>
 * <p>
 * This policy is used by endpoints that show course information to enrolled users.
 */
@Profile(PROFILE_CORE)
@Component("courseVisibilityPolicyProvider")
@Lazy
public class CourseVisibilityPolicy implements PolicyProvider<Course> {

    private final AccessPolicy<Course> policy;

    public CourseVisibilityPolicy() {
        this.policy = AccessPolicy.forResource(Course.class).named("course-visibility").section("Navigation").feature("Course Overview")
                .rule(when(memberOfGroup(Course::getTeachingAssistantGroupName, Course_.teachingAssistantGroupName)
                        .or(memberOfGroup(Course::getEditorGroupName, Course_.editorGroupName)).or(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName))
                        .or(isAdmin())).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT))
                .rule(when(memberOfGroup(Course::getStudentGroupName, Course_.studentGroupName).and(hasStarted(Course::getStartDate, Course_.startDate))).thenAllow()
                        .documentedFor(STUDENT).withNote("if enrolled + started"))
                .denyByDefault();
    }

    @Override
    public AccessPolicy<Course> getPolicy() {
        return policy;
    }
}
