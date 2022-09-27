package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.CourseTestService;
import de.tum.in.www1.artemis.util.ModelFactory;

class CourseGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private CourseTestService courseTestService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private UserRepository userRepo;

    @BeforeEach
    void setup() {
        courseTestService.setup(this);
        gitlabRequestMockProvider.enableMockingOfRequests();
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
    }

    @AfterEach
    void teardown() {
        database.resetDatabase();
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
    @WithMockUser(username = "student1", roles = "USER")
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
    void testUpdateCourseWithWrongShortName() throws Exception {
        courseTestService.testUpdateCourseWithWrongShortName();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseIsEmpty() throws Exception {
        courseTestService.testUpdateCourseIsEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testEditCourseWithPermission() throws Exception {
        courseTestService.testEditCourseWithPermission();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testEditCourseShouldPreserveAssociations() throws Exception {
        courseTestService.testEditCourseShouldPreserveAssociations();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseGroups() throws Exception {
        courseTestService.testUpdateCourseGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateOldMembersInCourse() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        course.setInstructorGroupName("new-editor-group");

        changeUserGroup("instructor1", Set.of(course.getTeachingAssistantGroupName()));
        changeUserGroup("tutor1", Set.of(course.getTeachingAssistantGroupName(), "new-editor-group"));
        changeUserGroup("tutor2", Set.of(course.getEditorGroupName()));

        gitlabRequestMockProvider.mockUpdateCoursePermissions(course, oldInstructorGroup, course.getEditorGroupName(), course.getTeachingAssistantGroupName());
        jenkinsRequestMockProvider.mockUpdateCoursePermissions(course, oldInstructorGroup, course.getEditorGroupName(), course.getTeachingAssistantGroupName(), false, false);
        course = request.putWithResponseBody("/api/courses/" + course.getId(), course, Course.class, HttpStatus.OK);

        assertThat(course.getInstructorGroupName()).isEqualTo("new-editor-group");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testSetPermissionsForNewGroupMembersInCourse() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        String oldInstructorGroup = course.getInstructorGroupName();
        String oldEditorGroup = course.getEditorGroupName();
        String oldTaGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setEditorGroupName("new-editor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        // Create editor in the course
        User user = ModelFactory.generateActivatedUser("new-editor");
        user.setGroups(Set.of("new-editor-group"));
        userRepo.save(user);

        user = ModelFactory.generateActivatedUser("new-ta");
        user.setGroups(Set.of("new-ta-group"));
        userRepo.save(user);

        user = ModelFactory.generateActivatedUser("new-instructor");
        user.setGroups(Set.of("new-instructor-group"));
        userRepo.save(user);

        gitlabRequestMockProvider.mockUpdateCoursePermissions(course, oldInstructorGroup, oldEditorGroup, oldTaGroup);
        jenkinsRequestMockProvider.mockUpdateCoursePermissions(course, oldInstructorGroup, course.getEditorGroupName(), course.getTeachingAssistantGroupName(), false, false);
        course = request.putWithResponseBody("/api/courses/" + course.getId(), course, Course.class, HttpStatus.OK);

        assertThat(course.getInstructorGroupName()).isEqualTo("new-instructor-group");
        assertThat(course.getEditorGroupName()).isEqualTo("new-editor-group");
        assertThat(course.getTeachingAssistantGroupName()).isEqualTo("new-ta-group");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testSetPermissionsForNewGroupMembersInCourseFails() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        course.setInstructorGroupName("new-instructor-group");

        // Create editor in the course
        User user = ModelFactory.generateActivatedUser("new-editor");
        user.setGroups(Set.of("new-instructor-group"));
        userRepo.save(user);

        gitlabRequestMockProvider.mockGetUserId(user.getLogin(), true, true);
        request.putWithResponseBody("/api/courses/" + course.getId(), course, Course.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testSetPermissionsForNewGroupMembersInCourseMemberDoesntExist() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        course.setInstructorGroupName("new-instructor-group");

        // Create editor in the course
        User user = ModelFactory.generateActivatedUser("new-editor");
        user.setGroups(Set.of("new-instructor-group"));
        userRepo.save(user);

        gitlabRequestMockProvider.mockFailOnGetUserById(user.getLogin());
        request.putWithResponseBody("/api/courses/" + course.getId(), course, Course.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testFailToUpdateOldMembersInCourse() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        course.setInstructorGroupName("new-editor-group");

        changeUserGroup("tutor1", Set.of(course.getTeachingAssistantGroupName()));

        var courseExercise = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(course);
        var exercise = courseExercise.stream().findFirst();
        assertThat(exercise).isPresent();

        var user = userRepo.findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName()).stream().findFirst();
        assertThat(user).isPresent();

        gitlabRequestMockProvider.mockFailToUpdateOldGroupMembers(exercise.get(), user.get());
        request.putWithResponseBody("/api/courses/" + course.getId(), course, Course.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Changes the group of the user.
     *
     * @param userLogin the login of the user
     * @param groups the groups to change
     */
    private void changeUserGroup(String userLogin, Set<String> groups) {
        Optional<User> user = userRepo.findOneWithGroupsByLogin(userLogin);
        assertThat(user).isPresent();

        User updatedUser = user.get();
        updatedUser.setGroups(groups);

        userRepo.save(updatedUser);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseGroupsFailsToGetUser() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        course.setInstructorGroupName("new-instructor-group");

        Optional<User> user = userRepo.findOneWithGroupsByLogin("instructor1");
        assertThat(user).isPresent();

        gitlabRequestMockProvider.mockFailToGetUserWhenUpdatingOldMembers(user.get());
        request.putWithResponseBody("/api/courses/" + course.getId(), course, Course.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseGroupsFailsToRemoveOldMember() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        course.setInstructorGroupName("new-instructor-group");

        Optional<User> user = userRepo.findOneWithGroupsByLogin("instructor1");
        assertThat(user).isPresent();

        var exercise = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(course).stream().findFirst();
        assertThat(exercise).isPresent();

        gitlabRequestMockProvider.mockFailToRemoveOldMember(exercise.get(), user.get());
        request.putWithResponseBody("/api/courses/" + course.getId(), course, Course.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseGroups_InExternalCiUserManagement_failToRemoveUser() throws Exception {
        courseTestService.testUpdateCourseGroups_InExternalCiUserManagement_failToRemoveUser();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseGroups_InExternalCiUserManagement_failToAddUser() throws Exception {
        courseTestService.testUpdateCourseGroups_InExternalCiUserManagement_failToAddUser();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetCourseWithoutPermission() throws Exception {
        courseTestService.testGetCourseWithoutPermission();
    }

    @Test
    @WithMockUser(username = "tutor6", roles = "TA")
    void testGetCourse_tutorNotInCourse() throws Exception {
        courseTestService.testGetCourse_tutorNotInCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCoursesWithPermission() throws Exception {
        courseTestService.testGetCoursesWithPermission();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCoursesWithQuizExercises() throws Exception {
        courseTestService.testGetCoursesWithQuizExercises();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetCourseForDashboard() throws Exception {
        courseTestService.testGetCourseForDashboard();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetAllCoursesForDashboard() throws Exception {
        courseTestService.testGetAllCoursesForDashboard();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetCoursesWithoutActiveExercises() throws Exception {
        courseTestService.testGetCoursesWithoutActiveExercises();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetCoursesAccurateTimezoneEvaluation() throws Exception {
        courseTestService.testGetCoursesAccurateTimezoneEvaluation();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllCoursesWithUserStats() throws Exception {
        courseTestService.testGetAllCoursesWithUserStats();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCourseWithOrganizations() throws Exception {
        courseTestService.testGetCourseWithOrganizations();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetCoursesToRegisterAndAccurateTimeZoneEvaluation() throws Exception {
        courseTestService.testGetCoursesToRegisterAndAccurateTimeZoneEvaluation();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetCourseForAssessmentDashboardWithStats() throws Exception {
        courseTestService.testGetCourseForAssessmentDashboardWithStats();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCourseForAssessmentDashboard_averageRatingComputedCorrectly() throws Exception {
        courseTestService.testGetCourseForAssessmentDashboard_averageRatingComputedCorrectly();
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    void testGetCourseForInstructorDashboardWithStats_instructorNotInCourse() throws Exception {
        courseTestService.testGetCourseForInstructorDashboardWithStats_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = "tutor6", roles = "TA")
    void testGetCourseForAssessmentDashboardWithStats_tutorNotInCourse() throws Exception {
        courseTestService.testGetCourseForAssessmentDashboardWithStats_tutorNotInCourse();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withoutAssessments() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withoutAssessments();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessments() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessments();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndComplaints() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaints();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndFeedbackRequests() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndFeedbackRequests();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndFeedBackRequestsAndResponses() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndFeedBackRequestsAndResponses();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses_Large() throws Exception {
        courseTestService.testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses_Large();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCourse() throws Exception {
        courseTestService.testGetCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCategoriesInCourse() throws Exception {
        courseTestService.testGetCategoriesInCourse();
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    void testGetCategoriesInCourse_instructorNotInCourse() throws Exception {
        courseTestService.testGetCategoriesInCourse_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = "ab12cde")
    void testRegisterForCourse() throws Exception {
        courseTestService.testRegisterForCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testAddTutorAndInstructorToCourse_failsToAddUserToGroup() throws Exception {
        courseTestService.testAddTutorAndEditorAndInstructorToCourse_failsToAddUserToGroup(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testRemoveTutorFromCourse_failsToRemoveUserFromGroup() throws Exception {
        courseTestService.testRemoveTutorFromCourse_failsToRemoveUserFromGroup();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testRemoveTutorFromCourse_removeUserFromGitlabGroupFails() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        database.addProgrammingExerciseToCourse(course, false);

        Optional<User> optionalTutor = userRepo.findOneWithGroupsByLogin("tutor1");
        assertThat(optionalTutor).isPresent();

        String tutorGroup = course.getTeachingAssistantGroupName();
        User tutor = optionalTutor.get();

        gitlabRequestMockProvider.mockUpdateBasicUserInformation(tutor.getLogin(), tutor, false);
        gitlabRequestMockProvider.mockRemoveUserFromGroup(1L, tutorGroup, Optional.of(new GitLabApiException("Forbidden", 403)));
        request.delete("/api/courses/" + course.getId() + "/tutors/" + tutor.getLogin(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "ab12cde")
    void testRegisterForCourse_notMeetsDate() throws Exception {
        courseTestService.testRegisterForCourse_notMeetsDate();
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
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    void testUpdateCourse_instructorNotInCourse() throws Exception {
        courseTestService.testUpdateCourse_instructorNotInCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllStudentsOrTutorsOrInstructorsInCourse() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void searchForStudentsInCourse() throws Exception {
        courseTestService.searchStudentsInCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllEditorsInCourse() throws Exception {
        courseTestService.testGetAllEditorsInCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testAddStudentOrTutorOrInstructorToCourse() throws Exception {
        courseTestService.testAddStudentOrTutorOrEditorOrInstructorToCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser() throws Exception {
        courseTestService.testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testRemoveStudentOrTutorOrInstructorFromCourse() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testRemoveStudentOrTutorOrInstructorFromCourse_WithNonExistingUser() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_WithNonExistingUser();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden() throws Exception {
        courseTestService.testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetLockedSubmissionsForCourseAsTutor() throws Exception {
        courseTestService.testGetLockedSubmissionsForCourseAsTutor();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetLockedSubmissionsForCourseAsStudent() throws Exception {
        courseTestService.testGetLockedSubmissionsForCourseAsStudent();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testArchiveCourseAsStudent_forbidden() throws Exception {
        courseTestService.testArchiveCourseAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testArchiveCourseAsTutor_forbidden() throws Exception {
        courseTestService.testArchiveCourseAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testArchiveCourseWithTestModelingAndFileUploadExercises() throws Exception {
        courseTestService.testArchiveCourseWithTestModelingAndFileUploadExercises();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportCourse_cannotCreateTmpDir() throws Exception {
        courseTestService.testExportCourse_cannotCreateTmpDir();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportCourse_cannotCreateCourseExercisesDir() throws Exception {
        courseTestService.testExportCourse_cannotCreateCourseExercisesDir();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportCourseExam_cannotCreateTmpDir() throws Exception {
        courseTestService.testExportCourseExam_cannotCreateTmpDir();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportCourseExam_cannotCreateExamExercisesDir() throws Exception {
        courseTestService.testExportCourseExam_cannotCreateExamsDir();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDownloadCourseArchiveAsStudent_forbidden() throws Exception {
        courseTestService.testDownloadCourseArchiveAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testDownloadCourseArchiveAsTutor_forbidden() throws Exception {
        courseTestService.testDownloadCourseArchiveAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDownloadCourseArchiveAsInstructor_not_found() throws Exception {
        courseTestService.testDownloadCourseArchiveAsInstructor_not_found();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDownloadCourseArchiveAsInstructor() throws Exception {
        courseTestService.testDownloadCourseArchiveAsInstructor();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCleanupCourseAsStudent_forbidden() throws Exception {
        courseTestService.testCleanupCourseAsStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testCleanupCourseAsTutor_forbidden() throws Exception {
        courseTestService.testCleanupCourseAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCleanupCourseAsInstructor_no_Archive() throws Exception {
        courseTestService.testCleanupCourseAsInstructor_no_Archive();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCleanupCourseAsInstructor() throws Exception {
        courseTestService.testCleanupCourseAsInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCourseTitle() throws Exception {
        // Only user and role matter, so we can re-use the logic
        courseTestService.testGetCourseTitle();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllCoursesForManagementOverview() throws Exception {
        courseTestService.testGetAllCoursesForManagementOverview();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetExercisesForCourseOverview() throws Exception {
        courseTestService.testGetExercisesForCourseOverview();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetExerciseStatsForCourseOverview() throws Exception {
        courseTestService.testGetExerciseStatsForCourseOverview();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetExerciseStatsForCourseOverviewWithPastExercises() throws Exception {
        courseTestService.testGetExerciseStatsForCourseOverviewWithPastExercises();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCourseManagementDetailData() throws Exception {
        courseTestService.testGetCourseManagementDetailData();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetCourseManagementDetailDataForFutureCourse() throws Exception {
        courseTestService.testGetCourseManagementDetailDataForFutureCourse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testAddUsersToCourseGroup() throws Exception {
        String group = "students";
        String registrationNumber1 = "1234567";
        String registrationNumber2 = "2345678";
        courseTestService.testAddUsersToCourseGroup(group, registrationNumber1, registrationNumber2);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithValidStartAndEndDate() throws Exception {
        courseTestService.testCreateCourseWithValidStartAndEndDate();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateCourseWithInvalidStartAndEndDate() throws Exception {
        courseTestService.testCreateCourseWithInvalidStartAndEndDate();
    }
}
