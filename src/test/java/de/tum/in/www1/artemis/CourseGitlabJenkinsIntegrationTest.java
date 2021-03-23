package de.tum.in.www1.artemis;

import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.util.CourseTestService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class CourseGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    CourseTestService courseTestService;

    @BeforeEach
    public void setup() {
        courseTestService.setup(this, this.groupNotificationService);
        gitlabRequestMockProvider.enableMockingOfRequests();
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
    }

    @AfterEach
    public void teardown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithPermission() throws Exception {
        courseTestService.testCreateCourseWithPermission();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithSameShortName() throws Exception {
        courseTestService.testCreateCourseWithSameShortName();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithNegativeMaxComplainNumber() throws Exception {
        courseTestService.testCreateCourseWithNegativeMaxComplainNumber();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithNegativeMaxComplainTimeDays() throws Exception {
        courseTestService.testCreateCourseWithNegativeMaxComplainTimeDays();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithNegativeMaxTeamComplainNumber() throws Exception {
        courseTestService.testCreateCourseWithNegativeMaxTeamComplainNumber();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithModifiedMaxComplainTimeDaysAndMaxComplains() throws Exception {
        courseTestService.testCreateCourseWithModifiedMaxComplainTimeDaysAndMaxComplains();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithCustomNonExistingGroupNames() throws Exception {
        courseTestService.testCreateCourseWithCustomNonExistingGroupNames();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithOptions() throws Exception {
        courseTestService.testCreateCourseWithOptions();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDeleteCourseWithPermission() throws Exception {
        courseTestService.testDeleteCourseWithPermission();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDeleteNotExistingCourse() throws Exception {
        courseTestService.testDeleteNotExistingCourse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateCourseWithoutPermission() throws Exception {
        courseTestService.testCreateCourseWithoutPermission();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithWrongShortName() throws Exception {
        courseTestService.testCreateCourseWithWrongShortName();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateCourseWithWrongShortName() throws Exception {
        courseTestService.testUpdateCourseWithWrongShortName();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateCourseWithoutId() throws Exception {
        courseTestService.testUpdateCourseWithoutId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateCourseIsEmpty() throws Exception {
        courseTestService.testUpdateCourseIsEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditCourseWithPermission() throws Exception {
        courseTestService.testEditCourseWithPermission();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateCourseGroups() throws Exception {
        courseTestService.testUpdateCourseGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateCourseGroups_InExternalCiUserManagement_failToRemoveUser() throws Exception {
        courseTestService.testUpdateCourseGroups_InExternalCiUserManagement_failToRemoveUser();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateCourseGroups_InExternalCiUserManagement_failToAddUser() throws Exception {
        courseTestService.testUpdateCourseGroups_InExternalCiUserManagement_failToAddUser();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetCourseWithoutPermission() throws Exception {
        courseTestService.testGetCourseWithoutPermission();
    }

    @Test
    @WithMockUser(username = "tutor6", roles = "TA")
    public void testGetCourse_tutorNotInCourse() throws Exception {
        courseTestService.testGetCourse_tutorNotInCourse();
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testGetCourseWithExercisesAndRelevantParticipationsWithoutPermissions() throws Exception {
        courseTestService.testGetCourseWithExercisesAndRelevantParticipationsWithoutPermissions();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCoursesWithPermission() throws Exception {
        courseTestService.testGetCoursesWithPermission();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCoursesWithQuizExercises() throws Exception {
        courseTestService.testGetCoursesWithQuizExercises();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetCourseForDashboard() throws Exception {
        courseTestService.testGetCourseForDashboard();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetAllCoursesForDashboard() throws Exception {
        courseTestService.testGetAllCoursesForDashboard();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetCoursesWithoutActiveExercises() throws Exception {
        courseTestService.testGetCoursesWithoutActiveExercises();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetCoursesAccurateTimezoneEvaluation() throws Exception {
        courseTestService.testGetCoursesAccurateTimezoneEvaluation();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllCoursesWithUserStats() throws Exception {
        courseTestService.testGetAllCoursesWithUserStats();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetCoursesToRegisterAndAccurateTimeZoneEvaluation() throws Exception {
        courseTestService.testGetCoursesToRegisterAndAccurateTimeZoneEvaluation();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetCourseForAssessmentDashboardWithStats() throws Exception {
        courseTestService.testGetCourseForAssessmentDashboardWithStats();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCourseForInstructorDashboardWithStats() throws Exception {
        courseTestService.testGetCourseForInstructorDashboardWithStats();
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testGetCourseForInstructorDashboardWithStats_instructorNotInCourse() throws Exception {
        courseTestService.testGetCourseForInstructorDashboardWithStats_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = "tutor6", roles = "TA")
    public void testGetCourseForAssessmentDashboardWithStats_tutorNotInCourse() throws Exception {
        courseTestService.testGetCourseForAssessmentDashboardWithStats_tutorNotInCourse();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAssessmentDashboardStats_withoutAssessments() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withoutAssessments();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAssessmentDashboardStats_withAssessments() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessments();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaints() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaints();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAssessmentDashboardStats_withAssessmentsAndFeedbackRequests() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndFeedbackRequests();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAssessmentDashboardStats_withAssessmentsAndFeedBackRequestsAndResponses() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndFeedBackRequestsAndResponses();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses_Large() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses_Large();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCourse() throws Exception {
        courseTestService.testGetCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCategoriesInCourse() throws Exception {
        courseTestService.testGetCategoriesInCourse();
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testGetCategoriesInCourse_instructorNotInCourse() throws Exception {
        courseTestService.testGetCategoriesInCourse_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = "ab12cde")
    public void testRegisterForCourse() throws Exception {
        courseTestService.testRegisterForCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddTutorAndInstructorToCourse_failsToAddUserToGroup() throws Exception {
        courseTestService.testAddTutorAndInstructorToCourse_failsToAddUserToGroup(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRemoveTutorFromCourse_failsToRemoveUserFromGroup() throws Exception {
        courseTestService.testRemoveTutorFromCourse_failsToRemoveUserFromGroup();
    }

    @Test
    @WithMockUser(username = "ab12cde")
    public void testRegisterForCourse_notMeetsDate() throws Exception {
        courseTestService.testRegisterForCourse_notMeetsDate();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateCourse_withExternalUserManagement_vcsUserManagementHasNotBeenCalled() throws Exception {
        var course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseTestService.getCourseRepo().save(course);

        request.put("/api/courses", course, HttpStatus.OK);

        verifyNoInteractions(versionControlService);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testUpdateCourse_instructorNotInCourse() throws Exception {
        courseTestService.testUpdateCourse_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddStudentOrTutorOrInstructorToCourse() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRemoveStudentOrTutorOrInstructorFromCourse() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRemoveStudentOrTutorOrInstructorFromCourse_WithNonExistingUser() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse_WithNonExistingUser();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetLockedSubmissionsForCourseAsTutor() throws Exception {
        courseTestService.testGetLockedSubmissionsForCourseAsTutor();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetLockedSubmissionsForCourseAsStudent() throws Exception {
        courseTestService.testGetLockedSubmissionsForCourseAsStudent();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testArchiveCourseAsStudent_forbidden() throws Exception {
        courseTestService.testArchiveCourseAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testArchiveCourseAsTutor_forbidden() throws Exception {
        courseTestService.testArchiveCourseAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testArchiveCourseWithTestModelingAndFileUploadExercises() throws Exception {
        courseTestService.testArchiveCourseWithTestModelingAndFileUploadExercises();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDownloadCourseArchiveAsStudent_forbidden() throws Exception {
        courseTestService.testDownloadCourseArchiveAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDownloadCourseArchiveAsTutor_forbidden() throws Exception {
        courseTestService.testDownloadCourseArchiveAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDownloadCourseArchiveAsInstructor_not_found() throws Exception {
        courseTestService.testDownloadCourseArchiveAsInstructor_not_found();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDownloadCourseArchiveAsInstructor() throws Exception {
        courseTestService.testDownloadCourseArchiveAsInstructor();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCleanupCourseAsStudent_forbidden() throws Exception {
        courseTestService.testCleanupCourseAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCleanupCourseAsTutor_forbidden() throws Exception {
        courseTestService.testCleanupCourseAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCleanupCourseAsInstructor_no_Archive() throws Exception {
        courseTestService.testCleanupCourseAsInstructor_no_Archive();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCleanupCourseAsInstructor() throws Exception {
        courseTestService.testCleanupCourseAsInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCourseTitle() throws Exception {
        // Only user and role matter, so we can re-use the logic
        courseTestService.testGetCourseTitle();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetCourseTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        courseTestService.testGetCourseTitle();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetCourseTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        courseTestService.testGetCourseTitle();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllCoursesForManagementOverview() throws Exception {
        courseTestService.testGetAllCoursesForManagementOverview();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetExercisesForCourseOverview() throws Exception {
        courseTestService.testGetExercisesForCourseOverview();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetExerciseStatsForCourseOverview() throws Exception {
        courseTestService.testGetExerciseStatsForCourseOverview();
    }
}
