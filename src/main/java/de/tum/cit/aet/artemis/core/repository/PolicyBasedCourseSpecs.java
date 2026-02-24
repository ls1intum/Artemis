package de.tum.cit.aet.artemis.core.repository;

import java.time.ZonedDateTime;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;

/**
 * Factory class for creating Course specifications that are automatically generated
 * from access policy definitions. This ensures that database queries and runtime
 * authorization checks use the exact same logic.
 * <p>
 * <strong>How it works:</strong>
 * <ol>
 * <li>Access policies in {@link de.tum.cit.aet.artemis.core.security.policy.definitions.CourseAccessPolicies}
 * define the access rules using {@link de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions}</li>
 * <li>The {@link AccessPolicy#toSpecification(Set, boolean)} method converts these policies to JPA Specifications</li>
 * <li>This class provides convenient wrapper methods that inject the policy beans and generate specs</li>
 * </ol>
 * <p>
 * <strong>Benefits:</strong>
 * <ul>
 * <li><b>Single source of truth:</b> Access logic defined once in the policy</li>
 * <li><b>Automatic synchronization:</b> Updating a policy automatically updates queries</li>
 * <li><b>Type-safe:</b> Compile-time checks via JPA Criteria API</li>
 * <li><b>Composable:</b> Can be combined with other specifications</li>
 * </ul>
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>{@code
 *
 * // In repository default method:
 * public List<Course> findCoursesWithStaffAccess(Set<String> userGroups, boolean isAdmin) {
 *     var spec = policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin);
 *     return findAll(spec);
 * }
 *
 * // Combine with other specifications:
 * var spec = CourseSpecs.and(policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin), CourseSpecs.isNotEnded(now), CourseSpecs.distinct());
 * }</pre>
 *
 * @see de.tum.cit.aet.artemis.core.security.policy.definitions.CourseAccessPolicies
 * @see de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions
 */
@Component
public class PolicyBasedCourseSpecs {

    private final AccessPolicy<Course> courseVisibilityPolicy;

    private final AccessPolicy<Course> courseStudentAccessPolicy;

    private final AccessPolicy<Course> courseStaffAccessPolicy;

    private final AccessPolicy<Course> courseEditorAccessPolicy;

    private final AccessPolicy<Course> courseInstructorAccessPolicy;

    /**
     * Constructor that injects all course access policy beans.
     * Spring automatically provides the policy beans defined in {@link de.tum.cit.aet.artemis.core.security.policy.definitions.CourseAccessPolicies}.
     *
     * @param courseVisibilityPolicy       the course visibility policy bean
     * @param courseStudentAccessPolicy    the course student access policy bean
     * @param courseStaffAccessPolicy      the course staff access policy bean
     * @param courseEditorAccessPolicy     the course editor access policy bean
     * @param courseInstructorAccessPolicy the course instructor access policy bean
     */
    public PolicyBasedCourseSpecs(AccessPolicy<Course> courseVisibilityPolicy, AccessPolicy<Course> courseStudentAccessPolicy, AccessPolicy<Course> courseStaffAccessPolicy,
            AccessPolicy<Course> courseEditorAccessPolicy, AccessPolicy<Course> courseInstructorAccessPolicy) {
        this.courseVisibilityPolicy = courseVisibilityPolicy;
        this.courseStudentAccessPolicy = courseStudentAccessPolicy;
        this.courseStaffAccessPolicy = courseStaffAccessPolicy;
        this.courseEditorAccessPolicy = courseEditorAccessPolicy;
        this.courseInstructorAccessPolicy = courseInstructorAccessPolicy;
    }

    /**
     * Creates a specification based on the course visibility policy.
     * <p>
     * <strong>Policy:</strong> {@code courseVisibilityPolicy}
     * <br>
     * <strong>Allows:</strong>
     * <ul>
     * <li>Teaching assistants, editors, instructors, and admins can always see a course</li>
     * <li>Students can see a course only if it has started (start date is null or in the past)</li>
     * </ul>
     * <p>
     * <strong>Automatically synced:</strong> This specification is generated from the policy definition.
     * If the policy changes, this specification automatically reflects those changes.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification matching the course visibility policy
     */
    @NonNull
    public Specification<Course> withVisibilityAccess(Set<String> userGroups, boolean isAdmin) {
        return courseVisibilityPolicy.toSpecification(userGroups, isAdmin);
    }

    /**
     * Creates a specification based on the course student access policy.
     * <p>
     * <strong>Policy:</strong> {@code courseStudentAccessPolicy}
     * <br>
     * <strong>Allows:</strong> Any enrolled user (student, TA, editor, instructor) or admin
     * <p>
     * <strong>Automatically synced:</strong> This specification is generated from the policy definition.
     * If the policy changes, this specification automatically reflects those changes.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification matching the course student access policy
     */
    @NonNull
    public Specification<Course> withStudentAccess(Set<String> userGroups, boolean isAdmin) {
        return courseStudentAccessPolicy.toSpecification(userGroups, isAdmin);
    }

    /**
     * Creates a specification based on the course staff access policy.
     * <p>
     * <strong>Policy:</strong> {@code courseStaffAccessPolicy}
     * <br>
     * <strong>Allows:</strong> Teaching assistants, editors, instructors, and admins
     * <p>
     * <strong>Automatically synced:</strong> This specification is generated from the policy definition.
     * If the policy changes, this specification automatically reflects those changes.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification matching the course staff access policy
     */
    @NonNull
    public Specification<Course> withStaffAccess(Set<String> userGroups, boolean isAdmin) {
        return courseStaffAccessPolicy.toSpecification(userGroups, isAdmin);
    }

    /**
     * Creates a specification based on the course editor access policy.
     * <p>
     * <strong>Policy:</strong> {@code courseEditorAccessPolicy}
     * <br>
     * <strong>Allows:</strong> Editors, instructors, and admins
     * <p>
     * <strong>Automatically synced:</strong> This specification is generated from the policy definition.
     * If the policy changes, this specification automatically reflects those changes.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification matching the course editor access policy
     */
    @NonNull
    public Specification<Course> withEditorAccess(Set<String> userGroups, boolean isAdmin) {
        return courseEditorAccessPolicy.toSpecification(userGroups, isAdmin);
    }

    /**
     * Creates a specification based on the course instructor access policy.
     * <p>
     * <strong>Policy:</strong> {@code courseInstructorAccessPolicy}
     * <br>
     * <strong>Allows:</strong> Instructors and admins
     * <p>
     * <strong>Automatically synced:</strong> This specification is generated from the policy definition.
     * If the policy changes, this specification automatically reflects those changes.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification matching the course instructor access policy
     */
    @NonNull
    public Specification<Course> withInstructorAccess(Set<String> userGroups, boolean isAdmin) {
        return courseInstructorAccessPolicy.toSpecification(userGroups, isAdmin);
    }

    /**
     * Helper method to combine an access policy spec with temporal filtering.
     * <p>
     * <strong>Example:</strong> Find all active courses visible to the user:
     *
     * <pre>{@code
     *
     * var spec = policyBasedCourseSpecs.withVisibilityAccessAndActive(userGroups, isAdmin, now);
     * }</pre>
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @param now        the current time
     * @return combined specification for visibility access and active status
     */
    @NonNull
    public Specification<Course> withVisibilityAccessAndActive(Set<String> userGroups, boolean isAdmin, ZonedDateTime now) {
        return CourseSpecs.and(withVisibilityAccess(userGroups, isAdmin), CourseSpecs.isActive(now), CourseSpecs.distinct());
    }

    /**
     * Helper method to combine staff access policy with non-ended temporal filter.
     * <p>
     * <strong>Example:</strong> Find all non-ended courses with staff access:
     *
     * <pre>{@code
     *
     * var spec = policyBasedCourseSpecs.withStaffAccessAndNotEnded(userGroups, isAdmin, now);
     * }</pre>
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @param now        the current time
     * @return combined specification for staff access and non-ended status
     */
    @NonNull
    public Specification<Course> withStaffAccessAndNotEnded(Set<String> userGroups, boolean isAdmin, ZonedDateTime now) {
        return CourseSpecs.and(withStaffAccess(userGroups, isAdmin), CourseSpecs.isNotEnded(now), CourseSpecs.distinct());
    }

    /**
     * Helper method to combine editor access policy with title search.
     * <p>
     * <strong>Example:</strong> Search courses by title with editor access:
     *
     * <pre>{@code
     *
     * var spec = policyBasedCourseSpecs.withEditorAccessAndTitleSearch(userGroups, isAdmin, searchTerm);
     * }</pre>
     *
     * @param userGroups   the groups the user belongs to
     * @param isAdmin      whether the user is an admin
     * @param partialTitle optional title search term (can be null)
     * @return combined specification for editor access and title search
     */
    @Nullable
    public Specification<Course> withEditorAccessAndTitleSearch(Set<String> userGroups, boolean isAdmin, String partialTitle) {
        return CourseSpecs.and(withEditorAccess(userGroups, isAdmin), CourseSpecs.titleContains(partialTitle), CourseSpecs.distinct());
    }
}
