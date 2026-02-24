package de.tum.cit.aet.artemis.core.repository;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Course_;

/**
 * This class contains specifications to query for courses based on access rights.
 * Each specification mirrors the corresponding access policy defined in CourseAccessPolicies.java.
 */
public class CourseSpecs {

    /**
     * Creates a specification matching the courseStudentAccessPolicy.
     * Allows access if user is in student, TA, editor, instructor group, or is admin.
     * <p>
     * NOTE: This specification mirrors the courseStudentAccessPolicy defined in CourseAccessPolicies.java.
     * If access rules change, update both the policy definition and this specification.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification for student-level access
     */
    @NonNull
    public static Specification<Course> hasStudentAccess(Set<String> userGroups, boolean isAdmin) {
        return (root, query, criteriaBuilder) -> {
            if (isAdmin) {
                return criteriaBuilder.conjunction(); // admin has access to all
            }

            // c.studentGroupName IN :userGroups
            // OR c.teachingAssistantGroupName IN :userGroups
            // OR c.editorGroupName IN :userGroups
            // OR c.instructorGroupName IN :userGroups
            Predicate studentGroup = root.get(Course_.STUDENT_GROUP_NAME).in(userGroups);
            Predicate taGroup = root.get(Course_.TEACHING_ASSISTANT_GROUP_NAME).in(userGroups);
            Predicate editorGroup = root.get(Course_.EDITOR_GROUP_NAME).in(userGroups);
            Predicate instructorGroup = root.get(Course_.INSTRUCTOR_GROUP_NAME).in(userGroups);

            return criteriaBuilder.or(studentGroup, taGroup, editorGroup, instructorGroup);
        };
    }

    /**
     * Creates a specification matching the courseStaffAccessPolicy.
     * Allows access if user is in TA, editor, instructor group, or is admin.
     * <p>
     * NOTE: This specification mirrors the courseStaffAccessPolicy defined in CourseAccessPolicies.java.
     * If access rules change, update both the policy definition and this specification.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification for staff-level access (TA and above)
     */
    @NonNull
    public static Specification<Course> hasStaffAccess(Set<String> userGroups, boolean isAdmin) {
        return (root, query, criteriaBuilder) -> {
            if (isAdmin) {
                return criteriaBuilder.conjunction(); // admin has access to all
            }

            // c.teachingAssistantGroupName IN :userGroups
            // OR c.editorGroupName IN :userGroups
            // OR c.instructorGroupName IN :userGroups
            Predicate taGroup = root.get(Course_.TEACHING_ASSISTANT_GROUP_NAME).in(userGroups);
            Predicate editorGroup = root.get(Course_.EDITOR_GROUP_NAME).in(userGroups);
            Predicate instructorGroup = root.get(Course_.INSTRUCTOR_GROUP_NAME).in(userGroups);

            return criteriaBuilder.or(taGroup, editorGroup, instructorGroup);
        };
    }

    /**
     * Creates a specification matching the courseEditorAccessPolicy.
     * Allows access if user is in editor, instructor group, or is admin.
     * <p>
     * NOTE: This specification mirrors the courseEditorAccessPolicy defined in CourseAccessPolicies.java.
     * If access rules change, update both the policy definition and this specification.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification for editor-level access (editor and above)
     */
    @NonNull
    public static Specification<Course> hasEditorAccess(Set<String> userGroups, boolean isAdmin) {
        return (root, query, criteriaBuilder) -> {
            if (isAdmin) {
                return criteriaBuilder.conjunction(); // admin has access to all
            }

            // c.editorGroupName IN :userGroups
            // OR c.instructorGroupName IN :userGroups
            Predicate editorGroup = root.get(Course_.EDITOR_GROUP_NAME).in(userGroups);
            Predicate instructorGroup = root.get(Course_.INSTRUCTOR_GROUP_NAME).in(userGroups);

            return criteriaBuilder.or(editorGroup, instructorGroup);
        };
    }

    /**
     * Creates a specification matching the courseInstructorAccessPolicy.
     * Allows access if user is in instructor group or is admin.
     * <p>
     * NOTE: This specification mirrors the courseInstructorAccessPolicy defined in CourseAccessPolicies.java.
     * If access rules change, update both the policy definition and this specification.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @return specification for instructor-level access
     */
    @NonNull
    public static Specification<Course> hasInstructorAccess(Set<String> userGroups, boolean isAdmin) {
        return (root, query, criteriaBuilder) -> {
            if (isAdmin) {
                return criteriaBuilder.conjunction(); // admin has access to all
            }

            // c.instructorGroupName IN :userGroups
            return root.get(Course_.INSTRUCTOR_GROUP_NAME).in(userGroups);
        };
    }

    /**
     * Creates a specification matching the courseVisibilityPolicy for students.
     * Students can see a course only if it has started (start date is null or in the past).
     * <p>
     * NOTE: This specification mirrors the courseVisibilityPolicy defined in CourseAccessPolicies.java.
     * If access rules change, update both the policy definition and this specification.
     *
     * @param userGroups the groups the user belongs to
     * @param isAdmin    whether the user is an admin
     * @param now        the current time
     * @return specification for course visibility based on start date
     */
    @NonNull
    public static Specification<Course> isVisibleToUser(Set<String> userGroups, boolean isAdmin, ZonedDateTime now) {
        return (root, query, criteriaBuilder) -> {
            if (isAdmin) {
                return criteriaBuilder.conjunction(); // admin can see all courses
            }

            // Staff (TA, editor, instructor) can always see the course
            Predicate isStaff = root.get(Course_.TEACHING_ASSISTANT_GROUP_NAME).in(userGroups);
            isStaff = criteriaBuilder.or(isStaff, root.get(Course_.EDITOR_GROUP_NAME).in(userGroups));
            isStaff = criteriaBuilder.or(isStaff, root.get(Course_.INSTRUCTOR_GROUP_NAME).in(userGroups));

            // Students can see if enrolled AND (course has started OR start date is null)
            Predicate isStudent = root.get(Course_.STUDENT_GROUP_NAME).in(userGroups);
            Predicate hasStarted = criteriaBuilder.or(criteriaBuilder.isNull(root.get(Course_.START_DATE)), criteriaBuilder.lessThanOrEqualTo(root.get(Course_.START_DATE), now));
            Predicate studentCanSee = criteriaBuilder.and(isStudent, hasStarted);

            return criteriaBuilder.or(isStaff, studentCanSee);
        };
    }

    /**
     * Creates a specification to filter courses by title (case-insensitive partial match).
     *
     * @param partialTitle the title search term
     * @return specification for title search, or null if partialTitle is null or empty
     */
    @Nullable
    public static Specification<Course> titleContains(String partialTitle) {
        if (partialTitle == null || partialTitle.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(Course_.TITLE)), "%" + partialTitle.toLowerCase() + "%");
    }

    /**
     * Creates a specification to filter courses that are currently active.
     * A course is active if:
     * - start date is null or in the past, AND
     * - end date is null or in the future
     *
     * @param now the current time
     * @return specification for active courses
     */
    @NonNull
    public static Specification<Course> isActive(ZonedDateTime now) {
        return (root, query, criteriaBuilder) -> {
            Predicate startCondition = criteriaBuilder.or(criteriaBuilder.isNull(root.get(Course_.START_DATE)),
                    criteriaBuilder.lessThanOrEqualTo(root.get(Course_.START_DATE), now));

            Predicate endCondition = criteriaBuilder.or(criteriaBuilder.isNull(root.get(Course_.END_DATE)), criteriaBuilder.greaterThanOrEqualTo(root.get(Course_.END_DATE), now));

            return criteriaBuilder.and(startCondition, endCondition);
        };
    }

    /**
     * Creates a specification to filter courses that are not yet ended.
     * A course is not ended if end date is null or in the future.
     *
     * @param now the current time
     * @return specification for non-ended courses
     */
    @NonNull
    public static Specification<Course> isNotEnded(ZonedDateTime now) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(criteriaBuilder.isNull(root.get(Course_.END_DATE)),
                criteriaBuilder.greaterThanOrEqualTo(root.get(Course_.END_DATE), now));
    }

    /**
     * Creates a specification to filter courses that have learning paths enabled.
     *
     * @return specification for courses with learning paths enabled
     */
    @NonNull
    public static Specification<Course> hasLearningPathsEnabled() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get(Course_.LEARNING_PATHS_ENABLED));
    }

    /**
     * Creates a specification to filter courses by a specific semester.
     *
     * @param semester the semester to filter by
     * @return specification for semester filter, or null if semester is null or empty
     */
    @Nullable
    public static Specification<Course> hasSemester(String semester) {
        if (semester == null || semester.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(Course_.SEMESTER), semester);
    }

    /**
     * Creates a specification to exclude test courses.
     *
     * @return specification to exclude test courses
     */
    @NonNull
    public static Specification<Course> excludeTestCourses() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get(Course_.TEST_COURSE));
    }

    /**
     * Creates a specification to filter courses where enrollment is currently active.
     * Enrollment is active if:
     * - enrollment is enabled, AND
     * - enrollment start date is in the past, AND
     * - enrollment end date is in the future
     *
     * @param now the current time
     * @return specification for enrollment active courses
     */
    @NonNull
    public static Specification<Course> hasActiveEnrollment(ZonedDateTime now) {
        return (root, query, criteriaBuilder) -> {
            Predicate enrollmentEnabled = criteriaBuilder.isTrue(root.get(Course_.ENROLLMENT_ENABLED));
            Predicate enrollmentStarted = criteriaBuilder.lessThanOrEqualTo(root.get(Course_.ENROLLMENT_START_DATE), now);
            Predicate enrollmentNotEnded = criteriaBuilder.greaterThanOrEqualTo(root.get(Course_.ENROLLMENT_END_DATE), now);

            return criteriaBuilder.and(enrollmentEnabled, enrollmentStarted, enrollmentNotEnded);
        };
    }

    /**
     * Creates a specification to get distinct results.
     *
     * @return specification for distinct results
     */
    @NonNull
    public static Specification<Course> distinct() {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }
            return null;
        };
    }

    /**
     * Helper method to combine specifications with AND logic, ignoring null specifications.
     *
     * @param specs the specifications to combine
     * @return combined specification, or null if all specs are null
     */
    @SafeVarargs
    @Nullable
    public static Specification<Course> and(Specification<Course>... specs) {
        Specification<Course> result = null;
        for (Specification<Course> spec : specs) {
            if (spec != null) {
                result = result == null ? spec : result.and(spec);
            }
        }
        return result;
    }

    /**
     * Helper method to combine specifications with OR logic, ignoring null specifications.
     *
     * @param specs the specifications to combine
     * @return combined specification, or null if all specs are null
     */
    @SafeVarargs
    @Nullable
    public static Specification<Course> or(Specification<Course>... specs) {
        Specification<Course> result = null;
        for (Specification<Course> spec : specs) {
            if (spec != null) {
                result = result == null ? spec : result.or(spec);
            }
        }
        return result;
    }
}
