package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketPermission;
import de.tum.in.www1.artemis.util.CourseTestService;
import de.tum.in.www1.artemis.util.ModelFactory;

class CourseBitbucketBambooJiraIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "courseebitbubamboojira";

    @Autowired
    private CourseTestService courseTestService;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        participantScoreScheduleService.activate();
        courseTestService.setup(TEST_PREFIX, this);
        jiraRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithPermission() throws Exception {
        courseTestService.testCreateCourseWithPermission();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithSameShortName() throws Exception {
        courseTestService.testCreateCourseWithSameShortName();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithNegativeMaxComplainNumber() throws Exception {
        courseTestService.testCreateCourseWithNegativeMaxComplainNumber();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithNegativeMaxComplainTimeDays() throws Exception {
        courseTestService.testCreateCourseWithNegativeMaxComplainTimeDays();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithNegativeMaxTeamComplainNumber() throws Exception {
        courseTestService.testCreateCourseWithNegativeMaxTeamComplainNumber();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithNegativeMaxComplaintTextLimit() throws Exception {
        courseTestService.testCreateCourseWithNegativeMaxComplaintTextLimit();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithNegativeMaxComplaintResponseTextLimit() throws Exception {
        courseTestService.testCreateCourseWithNegativeMaxComplaintResponseTextLimit();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithModifiedMaxComplainTimeDaysAndMaxComplains() throws Exception {
        courseTestService.testCreateCourseWithModifiedMaxComplainTimeDaysAndMaxComplains();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithCustomNonExistingGroupNames() throws Exception {
        courseTestService.testCreateCourseWithCustomNonExistingGroupNames();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithOptions() throws Exception {
        courseTestService.testCreateCourseWithOptions();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseWithPermission() throws Exception {
        courseTestService.testDeleteCourseWithPermission();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteNotExistingCourse() throws Exception {
        courseTestService.testDeleteNotExistingCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateCourseWithoutPermission() throws Exception {
        courseTestService.testCreateCourseWithoutPermission();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithWrongShortName() throws Exception {
        courseTestService.testCreateCourseWithWrongShortName();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateDefaultCourseChannelsOnCourseCreation() throws Exception {
        courseTestService.testCreateCourseWithDefaultChannels();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseIsEmpty() throws Exception {
        courseTestService.testUpdateCourseIsEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEditCourseWithPermission() throws Exception {
        courseTestService.testEditCourseWithPermission();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEditCourseShouldPreserveAssociations() throws Exception {
        courseTestService.testEditCourseShouldPreserveAssociations();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseGroups() throws Exception {
        bitbucketRequestMockProvider.mockRevokeGroupPermissionFromAnyProject("instructor");
        bitbucketRequestMockProvider.mockGrantGroupPermissionToAnyProject("new-instructor-group", BitbucketPermission.PROJECT_ADMIN);
        bitbucketRequestMockProvider.mockRevokeGroupPermissionFromAnyProject("editor");
        bitbucketRequestMockProvider.mockGrantGroupPermissionToAnyProject("new-editor-group", BitbucketPermission.PROJECT_WRITE);
        bitbucketRequestMockProvider.mockRevokeGroupPermissionFromAnyProject("tutor");
        bitbucketRequestMockProvider.mockGrantGroupPermissionToAnyProject("new-ta-group", BitbucketPermission.PROJECT_READ);
        courseTestService.testUpdateCourseGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateAndUpdateCourseWithCourseImage() throws Exception {
        courseTestService.testCreateAndUpdateCourseWithCourseImage();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateAndUpdateCourseWithPersistentCourseImageOnUpdate() throws Exception {
        courseTestService.testCreateAndUpdateCourseWithPersistentCourseImageOnUpdate();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateAndUpdateCourseWithRemoveCourseImageOnUpdate() throws Exception {
        courseTestService.testCreateAndUpdateCourseWithRemoveCourseImageOnUpdate();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateAndUpdateCourseWithSetNewImageDespiteRemoval() throws Exception {
        courseTestService.testCreateAndUpdateCourseWithSetNewImageDespiteRemoval();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWithoutPermission() throws Exception {
        courseTestService.testGetCourseWithoutPermission();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetCourse_tutorNotInCourse() throws Exception {
        courseTestService.testGetCourse_tutorNotInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCoursesWithPermission() throws Exception {
        courseTestService.testGetCoursesWithPermission();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCoursesWithQuizExercises() throws Exception {
        courseTestService.testGetCoursesWithQuizExercises();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @ValueSource(booleans = { true, false })
    void testGetCourseForDashboard(boolean userRefresh) throws Exception {
        courseTestService.testGetCourseForDashboard(userRefresh);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @ValueSource(booleans = { true, false })
    void testGetCourseForDashboardAccessDenied(boolean userRefresh) throws Exception {
        courseTestService.testGetCourseForDashboardAccessDenied(userRefresh);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseForDashboardForbiddenWithRegistrationPossible() throws Exception {
        courseTestService.testGetCourseForDashboardForbiddenWithRegistrationPossible();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseForRegistration() throws Exception {
        courseTestService.testGetCourseForRegistration();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseForRegistrationAccessDenied() throws Exception {
        courseTestService.testGetCourseForRegistrationAccessDenied();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "custom1", roles = { "USER", "TA", "EDITOR", "INSTRUCTOR" })
    @ValueSource(booleans = { true, false })
    void testGetAllCoursesForDashboardExams(boolean userRefresh) throws Exception {
        courseTestService.testGetAllCoursesForDashboardExams(userRefresh);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllCoursesForDashboard() throws Exception {
        courseTestService.testGetAllCoursesForDashboard();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCoursesWithoutActiveExercises() throws Exception {
        courseTestService.testGetCoursesWithoutActiveExercises();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCoursesAccurateTimezoneEvaluation() throws Exception {
        courseTestService.testGetCoursesAccurateTimezoneEvaluation();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllCoursesWithUserStats() throws Exception {
        courseTestService.testGetAllCoursesWithUserStats();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseWithOrganizations() throws Exception {
        courseTestService.testGetCourseWithOrganizations();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetCoursesToRegisterAndAccurateTimeZoneEvaluation() throws Exception {
        courseTestService.testGetCoursesForRegistrationAndAccurateTimeZoneEvaluation();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCourseForAssessmentDashboardWithStats() throws Exception {
        courseTestService.testGetCourseForAssessmentDashboardWithStats();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseForAssessmentDashboard_averageRatingComputedCorrectly() throws Exception {
        courseTestService.testGetCourseForAssessmentDashboard_averageRatingComputedCorrectly();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetCourseForInstructorDashboardWithStats_instructorNotInCourse() throws Exception {
        courseTestService.testGetCourseForInstructorDashboardWithStats_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetCourseForAssessmentDashboardWithStats_tutorNotInCourse() throws Exception {
        courseTestService.testGetCourseForAssessmentDashboardWithStats_tutorNotInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withoutAssessments() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withoutAssessments();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessments() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessments();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndComplaints() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaints();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndFeedbackRequests() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndFeedbackRequests();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndFeedBackRequestsAndResponses() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndFeedBackRequestsAndResponses();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses_Large() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses_Large();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourse() throws Exception {
        courseTestService.testGetCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCategoriesInCourse() throws Exception {
        courseTestService.testGetCategoriesInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetCategoriesInCourse_instructorNotInCourse() throws Exception {
        courseTestService.testGetCategoriesInCourse_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = "ab12cde")
    void testRegisterForCourse() throws Exception {
        bitbucketRequestMockProvider.mockUpdateUserDetails("ab12cde", "ab12cde@test.de", "ab12cdeFirst ab12cdeLast");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        courseTestService.testRegisterForCourse();
    }

    @Test
    @WithMockUser(username = "ab12cde")
    void testRegisterForCourse_notMeetsDate() throws Exception {
        courseTestService.testRegisterForCourse_notMeetsDate();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddTutorAndInstructorToCourse_failsToAddUserToGroup() throws Exception {
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "tutor1", TEST_PREFIX + "tutor1@test.de", TEST_PREFIX + "tutor1First " + TEST_PREFIX + "tutor1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "editor1", TEST_PREFIX + "editor1@test.de", TEST_PREFIX + "editor1First " + TEST_PREFIX + "editor1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "instructor1", TEST_PREFIX + "instructor1@test.de",
                TEST_PREFIX + "instructor1First " + TEST_PREFIX + "instructor1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        courseTestService.testAddTutorAndEditorAndInstructorToCourse_failsToAddUserToGroup(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRemoveTutorFromCourse_failsToRemoveUserFromGroup() throws Exception {
        courseTestService.testRemoveTutorFromCourse_failsToRemoveUserFromGroup();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRemoveTutorFromCourse_userNotFoundInJira_successful() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor", TEST_PREFIX + "editor",
                TEST_PREFIX + "instructor");
        course = courseRepo.save(course);
        database.addProgrammingExerciseToCourse(course, false);
        course = courseRepo.save(course);

        User tutor = userRepository.findOneWithGroupsByLogin(TEST_PREFIX + "tutor1").get();
        jiraRequestMockProvider.mockRemoveUserFromGroup(Set.of(course.getTeachingAssistantGroupName()), tutor.getLogin(), false, true);
        bitbucketRequestMockProvider.mockUpdateUserDetails(tutor.getLogin(), tutor.getEmail(), tutor.getName());
        bitbucketRequestMockProvider.mockRemoveUserFromGroup(tutor.getLogin(), course.getTeachingAssistantGroupName());
        request.delete("/api/courses/" + course.getId() + "/tutors/" + tutor.getLogin(), HttpStatus.OK);
        tutor = userRepository.findOneWithGroupsByLogin(TEST_PREFIX + "tutor1").get();
        assertThat(tutor.getGroups()).doesNotContain(course.getTeachingAssistantGroupName());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourse_withExternalUserManagement_vcsUserManagementHasNotBeenCalled() throws Exception {
        var course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        request.put("/api/courses", course, HttpStatus.OK);

        verifyNoInteractions(versionControlService);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testUpdateCourse_instructorNotInCourse() throws Exception {
        courseTestService.testUpdateCourse_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllStudentsOrTutorsOrInstructorsInCourse() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchForStudentsInCourse() throws Exception {
        courseTestService.searchStudentsInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForAllTutors_shouldReturnAllTutorsAndEditors() throws Exception {
        courseTestService.searchUsersInCourse_searchForAllTutors_shouldReturnAllTutorsAndEditors();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForAllInstructor_shouldReturnAllInstructors() throws Exception {
        courseTestService.searchUsersInCourse_searchForAllInstructor_shouldReturnAllInstructors();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForAllStudents_shouldReturnBadRequest() throws Exception {
        courseTestService.searchUsersInCourse_searchForAllStudents_shouldReturnBadRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForStudentsAndTooShortSearchTerm_shouldReturnBadRequest() throws Exception {
        courseTestService.searchUsersInCourse_searchForStudentsAndTooShortSearchTerm_shouldReturnBadRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForStudents_shouldReturnUsersMatchingSearchTerm() throws Exception {
        courseTestService.searchUsersInCourse_searchForStudents_shouldReturnUsersMatchingSearchTerm();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForAllTutorsAndInstructors_shouldReturnAllTutorsEditorsAndInstructors() throws Exception {
        courseTestService.searchUsersInCourse_searchForAllTutorsAndInstructors_shouldReturnAllTutorsEditorsAndInstructors();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForTutorsAndInstructors_shouldReturnUsersMatchingSearchTerm() throws Exception {
        courseTestService.searchUsersInCourse_searchForTutorsAndInstructors_shouldReturnUsersMatchingSearchTerm();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForStudentsTutorsAndInstructorsAndTooShortSearchTerm_shouldReturnBadRequest() throws Exception {
        courseTestService.searchUsersInCourse_searchForStudentsTutorsAndInstructorsAndTooShortSearchTerm_shouldReturnBadRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersInCourse_searchForStudentsTutorsEditorsAndInstructors_shouldReturnUsersMatchingSearchTerm() throws Exception {
        courseTestService.searchUsersInCourse_searchForStudentsTutorsEditorsAndInstructors_shouldReturnUsersMatchingSearchTerm();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllEditorsInCourse() throws Exception {
        courseTestService.testGetAllEditorsInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSearchStudentsAndTutorsAndInstructorsInCourse() throws Exception {
        courseTestService.testSearchStudentsAndTutorsAndInstructorsInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSearchStudentsAndTutorsAndInstructorsInOtherCourse_forbidden() throws Exception {
        courseTestService.testSearchStudentsAndTutorsAndInstructorsInOtherCourseForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddStudentOrTutorOrEditorOrInstructorToCourse() throws Exception {
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "student1", TEST_PREFIX + "student1@test.de",
                TEST_PREFIX + "student1First " + TEST_PREFIX + "student1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "tutor1", TEST_PREFIX + "tutor1@test.de", TEST_PREFIX + "tutor1First " + TEST_PREFIX + "tutor1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "editor1", TEST_PREFIX + "editor1@test.de", TEST_PREFIX + "editor1First " + TEST_PREFIX + "editor1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "instructor1", TEST_PREFIX + "instructor1@test.de",
                TEST_PREFIX + "instructor1First " + TEST_PREFIX + "instructor1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        courseTestService.testAddStudentOrTutorOrEditorOrInstructorToCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRemoveStudentOrTutorOrInstructorFromCourse() throws Exception {
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "student1", TEST_PREFIX + "student1@test.de",
                TEST_PREFIX + "student1First " + TEST_PREFIX + "student1Last");
        bitbucketRequestMockProvider.mockRemoveUserFromGroup(TEST_PREFIX + "student1", "tumuser");
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "tutor1", TEST_PREFIX + "tutor1@test.de", TEST_PREFIX + "tutor1First " + TEST_PREFIX + "tutor1Last");
        bitbucketRequestMockProvider.mockRemoveUserFromGroup(TEST_PREFIX + "tutor1", "tutor");
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "editor1", TEST_PREFIX + "editor1@test.de", TEST_PREFIX + "editor1First " + TEST_PREFIX + "editor1Last");
        bitbucketRequestMockProvider.mockRemoveUserFromGroup(TEST_PREFIX + "editor1", "editor");
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "instructor1", TEST_PREFIX + "instructor1@test.de",
                TEST_PREFIX + "instructor1First " + TEST_PREFIX + "instructor1Last");
        bitbucketRequestMockProvider.mockRemoveUserFromGroup(TEST_PREFIX + "instructor1", "instructor");
        bitbucketRequestMockProvider.mockUpdateUserDetails(TEST_PREFIX + "student1", TEST_PREFIX + "student1@test.de",
                TEST_PREFIX + "student1First " + TEST_PREFIX + "student1Last");
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRemoveStudentOrTutorOrInstructorFromCourse_WithNonExistingUser() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_WithNonExistingUser();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLockedSubmissionsForCourseAsTutor() throws Exception {
        courseTestService.testGetLockedSubmissionsForCourseAsTutor();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLockedSubmissionsForCourseAsStudent() throws Exception {
        courseTestService.testGetLockedSubmissionsForCourseAsStudent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testArchiveCourseAsStudent_forbidden() throws Exception {
        courseTestService.testArchiveCourseAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testArchiveCourseAsTutor_forbidden() throws Exception {
        courseTestService.testArchiveCourseAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveCourseWithTestModelingAndFileUploadExercises() throws Exception {
        courseTestService.testArchiveCourseWithTestModelingAndFileUploadExercises();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportModelingExercise() throws Exception {
        courseTestService.testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportModelingExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportFileUploadExercise() throws Exception {
        courseTestService.testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportFileUploadExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportTextExercise() throws Exception {
        courseTestService.testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportTextExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourse_cannotCreateTmpDir() throws Exception {
        courseTestService.testExportCourse_cannotCreateTmpDir();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourse_cannotCreateCourseExercisesDir() throws Exception {
        courseTestService.testExportCourse_cannotCreateCourseExercisesDir();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseExam_cannotCreateTmpDir() throws Exception {
        courseTestService.testExportCourseExam_cannotCreateTmpDir();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseExam_cannotCreateExamExercisesDir() throws Exception {
        courseTestService.testExportCourseExam_cannotCreateExamsDir();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDownloadCourseArchiveAsStudent_forbidden() throws Exception {
        courseTestService.testDownloadCourseArchiveAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDownloadCourseArchiveAsTutor_forbidden() throws Exception {
        courseTestService.testDownloadCourseArchiveAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadCourseArchiveAsInstructor_not_found() throws Exception {
        courseTestService.testDownloadCourseArchiveAsInstructor_not_found();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadCourseArchiveAsInstructor() throws Exception {
        courseTestService.testDownloadCourseArchiveAsInstructor();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCleanupCourseAsStudent_forbidden() throws Exception {
        courseTestService.testCleanupCourseAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCleanupCourseAsTutor_forbidden() throws Exception {
        courseTestService.testCleanupCourseAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCleanupCourseAsInstructor_no_Archive() throws Exception {
        courseTestService.testCleanupCourseAsInstructor_no_Archive();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCleanupCourseAsInstructor() throws Exception {
        courseTestService.testCleanupCourseAsInstructor();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        courseTestService.testGetCourseTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCourseTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        courseTestService.testGetCourseTitle();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void testGetCourseTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        courseTestService.testGetCourseTitle();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void testGetCourseTitleForNonExistingCourse() throws Exception {
        courseTestService.testGetCourseTitleForNonExistingCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllCoursesForManagementOverview() throws Exception {
        courseTestService.testGetAllCoursesForManagementOverview();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExercisesForCourseOverview() throws Exception {
        courseTestService.testGetExercisesForCourseOverview();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExerciseStatsForCourseOverview() throws Exception {
        courseTestService.testGetExerciseStatsForCourseOverview();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExerciseStatsForCourseOverviewWithPastExercises() throws Exception {
        courseTestService.testGetExerciseStatsForCourseOverviewWithPastExercises();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseManagementDetailData() throws Exception {
        courseTestService.testGetCourseManagementDetailData();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseManagementDetailDataForFutureCourse() throws Exception {
        courseTestService.testGetCourseManagementDetailDataForFutureCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddUsersToCourseGroup() throws Exception {
        String group = "students";
        String registrationNumber1 = "1234567";
        String registrationNumber2 = "2345678";
        String email = "test@mail";
        jiraRequestMockProvider.mockAddUserToGroup(group, false);
        jiraRequestMockProvider.mockAddUserToGroup(group, false);
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber1);
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber2);
        courseTestService.testAddUsersToCourseGroup(group, registrationNumber1, registrationNumber2, email);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateInvalidOnlineCourse() throws Exception {
        courseTestService.testCreateInvalidOnlineCourse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateValidOnlineCourse() throws Exception {
        courseTestService.testCreateValidOnlineCourse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateToOnlineCourse() throws Exception {
        courseTestService.testUpdateToOnlineCourse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testOnlineCourseConfigurationIsLazyLoaded() throws Exception {
        courseTestService.testOnlineCourseConfigurationIsLazyLoaded();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateOnlineCourseConfiguration() throws Exception {
        courseTestService.testUpdateOnlineCourseConfiguration();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseRemoveOnlineCourseConfiguration() throws Exception {
        courseTestService.testUpdateCourseRemoveOnlineCourseConfiguration();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseDeletesOnlineConfiguration() throws Exception {
        courseTestService.testDeleteCourseDeletesOnlineConfiguration();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateInvalidOnlineCourseConfiguration() throws Exception {
        courseTestService.testUpdateInvalidOnlineCourseConfiguration();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testInvalidOnlineCourseConfigurationNonUniqueRegistrationId() throws Exception {
        courseTestService.testInvalidOnlineCourseConfigurationNonUniqueRegistrationId();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateValidOnlineCourseConfigurationAsStudent_forbidden() throws Exception {
        courseTestService.testUpdateValidOnlineCourseConfigurationAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateValidOnlineCourseConfigurationNotOnlineCourse() throws Exception {
        courseTestService.testUpdateValidOnlineCourseConfigurationNotOnlineCourse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateValidOnlineCourseConfiguration_IdMismatch() throws Exception {
        courseTestService.testUpdateValidOnlineCourseConfiguration_IdMismatch();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateValidOnlineCourseConfiguration() throws Exception {
        courseTestService.testUpdateValidOnlineCourseConfiguration();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEditCourseRemoveExistingIcon() throws Exception {
        courseTestService.testEditCourseRemoveExistingIcon();
    }
}
