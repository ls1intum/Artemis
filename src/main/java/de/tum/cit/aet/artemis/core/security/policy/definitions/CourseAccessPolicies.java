package de.tum.cit.aet.artemis.core.security.policy.definitions;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.EDITOR;
import static de.tum.cit.aet.artemis.core.security.Role.INSTRUCTOR;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static de.tum.cit.aet.artemis.core.security.Role.SUPER_ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.TEACHING_ASSISTANT;
import static de.tum.cit.aet.artemis.core.security.policy.AccessPolicy.when;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.hasStarted;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.isAdmin;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.memberOfGroup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
    @Lazy
    public AccessPolicy<Course> courseVisibilityPolicy() {
        return AccessPolicy.forResource(Course.class).named("course-visibility").section("Navigation").feature("Course Overview")
                .rule(when(memberOfGroup(Course::getTeachingAssistantGroupName).or(memberOfGroup(Course::getEditorGroupName)).or(memberOfGroup(Course::getInstructorGroupName))
                        .or(isAdmin())).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT))
                .rule(when(memberOfGroup(Course::getStudentGroupName).and(hasStarted(Course::getStartDate))).thenAllow().documentedFor(STUDENT).withNote("if enrolled + started"))
                .denyByDefault();
    }

    /**
     * Defines the course student access policy (no start-date gate).
     * <ul>
     * <li>Any enrolled user (student, TA, editor, instructor) or admin can access.</li>
     * <li>All other users are denied access by default.</li>
     * </ul>
     * <p>
     * Endpoints using this policy:
     * <ul>
     * <li>Search Users in Course (CourseAccessResource#searchUsersInCourse)</li>
     * <li>Search Other Users in Course (CourseAccessResource#searchOtherUsersInCourse)</li>
     * <li>Search Course Members (CourseAccessResource#searchMembersOfCourse)</li>
     * </ul>
     *
     * @return the course student access policy
     */
    @Bean
    @Lazy
    public AccessPolicy<Course> courseStudentAccessPolicy() {
        return AccessPolicy.forResource(Course.class).named("course-student-access").section("Course Access")
                .features("Search Users in Course", "Search Other Users in Course", "Search Course Members")
                .rule(when(memberOfGroup(Course::getStudentGroupName).or(memberOfGroup(Course::getTeachingAssistantGroupName)).or(memberOfGroup(Course::getEditorGroupName))
                        .or(memberOfGroup(Course::getInstructorGroupName)).or(isAdmin())).thenAllow()
                        .documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT, STUDENT))
                .denyByDefault();
    }

    /**
     * Defines the course staff access policy (TA and above).
     * <ul>
     * <li>Teaching assistants, editors, instructors, and admins can access.</li>
     * <li>All other users (including students) are denied access by default.</li>
     * </ul>
     * <p>
     * Endpoints using this policy:
     * <ul>
     * <li>Assessment Dashboard (CourseManagementResource#getCourseForAssessmentDashboard)</li>
     * <li>View Course with Exercises (CourseManagementResource#getCourseWithExercises)</li>
     * <li>View Course Full Details (CourseManagementResource#getCourseWithExercisesAndLecturesAndCompetencies)</li>
     * <li>View Course Organizations (CourseManagementResource#getCourseWithOrganizations)</li>
     * <li>View Locked Submissions (CourseManagementResource#getLockedSubmissionsForCourse)</li>
     * <li>View Exercise Due Dates (CourseManagementResource#getAllExercisesWithDueDatesForCourse)</li>
     * <li>View Assessment Dashboard Stats (CourseStatsResource#getStatsForAssessmentDashboard)</li>
     * <li>View Active Students Statistics (CourseStatsResource#getActiveStudentsForCourseDetailView)</li>
     * <li>View Lifetime Statistics (CourseStatsResource#getActiveStudentsForCourseLiveTime)</li>
     * <li>View Management Detail (CourseStatsResource#getCourseDTOForDetailView)</li>
     * <li>Search Students in Course (CourseAccessResource#searchStudentsInCourse)</li>
     * </ul>
     *
     * @return the course staff access policy
     */
    @Bean
    @Lazy
    public AccessPolicy<Course> courseStaffAccessPolicy() {
        return AccessPolicy.forResource(Course.class).named("course-staff-access").section("Course Access")
                .features("Assessment Dashboard", "View Course with Exercises", "View Course Full Details", "View Course Organizations", "View Locked Submissions",
                        "View Exercise Due Dates", "View Assessment Dashboard Stats", "View Active Students Statistics", "View Lifetime Statistics", "View Management Detail",
                        "Search Students in Course")
                .rule(when(memberOfGroup(Course::getTeachingAssistantGroupName).or(memberOfGroup(Course::getEditorGroupName)).or(memberOfGroup(Course::getInstructorGroupName))
                        .or(isAdmin())).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT))
                .denyByDefault();
    }

    /**
     * Defines the course editor access policy (editor and above).
     * <ul>
     * <li>Editors, instructors, and admins can access.</li>
     * <li>All other users are denied access by default.</li>
     * </ul>
     * <p>
     * Endpoints using this policy:
     * <ul>
     * <li>View Exercise Categories (CourseManagementResource#getCategoriesInCourse)</li>
     * <li>View Existing Exercise Details (CourseManagementResource#getExistingExerciseDetails)</li>
     * </ul>
     *
     * @return the course editor access policy
     */
    @Bean
    @Lazy
    public AccessPolicy<Course> courseEditorAccessPolicy() {
        return AccessPolicy.forResource(Course.class).named("course-editor-access").section("Course Access").features("View Exercise Categories", "View Existing Exercise Details")
                .rule(when(memberOfGroup(Course::getEditorGroupName).or(memberOfGroup(Course::getInstructorGroupName)).or(isAdmin())).thenAllow().documentedFor(SUPER_ADMIN, ADMIN,
                        INSTRUCTOR, EDITOR))
                .denyByDefault();
    }

    /**
     * Defines the course instructor access policy (instructor and above).
     * <ul>
     * <li>Instructors and admins can access.</li>
     * <li>All other users are denied access by default.</li>
     * </ul>
     * <p>
     * Endpoints using this policy:
     * <ul>
     * <li>Update Course (CourseUpdateResource#updateCourse)</li>
     * <li>Archive Course (CourseArchiveResource#archiveCourse)</li>
     * <li>Download Course Archive (CourseArchiveResource#downloadCourseArchive)</li>
     * <li>Cleanup Course (CourseArchiveResource#cleanup)</li>
     * <li>View Import Summary (CourseMaterialImportResource#getImportSummary)</li>
     * <li>Import Course Material (CourseMaterialImportResource#importCourseMaterial)</li>
     * <li>List Course Users (CourseAccessResource#getStudents/Tutors/Editors/InstructorsInCourse)</li>
     * <li>Add User to Course (CourseAccessResource#addStudent/Tutor/Editor/InstructorToCourse)</li>
     * <li>Remove User from Course (CourseAccessResource#removeStudent/Tutor/Editor/InstructorFromCourse)</li>
     * <li>Batch Add Users to Group (CourseAccessResource#addUsersToCourseGroup)</li>
     * </ul>
     *
     * @return the course instructor access policy
     */
    @Bean
    @Lazy
    public AccessPolicy<Course> courseInstructorAccessPolicy() {
        return AccessPolicy.forResource(Course.class).named("course-instructor-access").section("Course Access")
                .features("Update Course", "Archive Course", "Download Course Archive", "Cleanup Course", "View Import Summary", "Import Course Material", "List Course Users",
                        "Add User to Course", "Remove User from Course", "Batch Add Users to Group")
                .rule(when(memberOfGroup(Course::getInstructorGroupName).or(isAdmin())).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR)).denyByDefault();
    }
}
