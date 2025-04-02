package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AEOLUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_APOLLON;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.Constants.TEST_REPO_NAME;
import static de.tum.cit.aet.artemis.core.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.connector.AeolusRequestMockProvider;
import de.tum.cit.aet.artemis.core.connector.JenkinsRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.service.ExamLiveEventsService;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.AeolusTarget;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsService;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobPermissionsService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCGitBranchService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCService;

// TODO: rewrite this test to use LocalVC
@ResourceLock("AbstractSpringIntegrationJenkinsLocalVcTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_ARTEMIS, PROFILE_CORE, PROFILE_SCHEDULING, PROFILE_LOCALVC, PROFILE_JENKINS, PROFILE_ATHENA, PROFILE_LTI, PROFILE_AEOLUS,
        PROFILE_APOLLON })
@TestPropertySource(properties = { "server.port=49153", "artemis.version-control.url=http://localhost:49153",
        "artemis.version-control.ssh-private-key-folder-path=${java.io.tmpdir}", "info.guided-tour.course-group-tutors=artemis-artemistutorial-tutors",
        "info.guided-tour.course-group-students=artemis-artemistutorial-students", "info.guided-tour.course-group-editors=artemis-artemistutorial-editors",
        "info.guided-tour.course-group-instructors=artemis-artemistutorial-instructors", "artemis.user-management.use-external=false",
        "artemis.user-management.course-enrollment.allowed-username-pattern=^(?!authorizationservicestudent2).*$",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_jenkins_localvc", "artemis.version-control.ssh-port=1235", "info.contact=test@localhost",
        "artemis.version-control.ssh-template-clone-url=ssh://git@localhost:1235/" })
public abstract class AbstractSpringIntegrationJenkinsLocalVcTest extends AbstractArtemisIntegrationTest {

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Jenkins using the corresponding RestTemplate.
    @MockitoSpyBean
    protected JenkinsService continuousIntegrationService;

    @MockitoSpyBean
    protected LocalVCService versionControlService;

    @MockitoSpyBean
    protected LocalVCGitBranchService localVCGitBranchService;

    @MockitoSpyBean
    protected JenkinsJobPermissionsService jenkinsJobPermissionsService;

    @MockitoSpyBean
    protected ProgrammingMessagingService programmingMessagingService;

    @MockitoSpyBean
    protected ResultWebsocketService resultWebsocketService;

    @Autowired
    protected JenkinsRequestMockProvider jenkinsRequestMockProvider;

    @Autowired
    protected AeolusRequestMockProvider aeolusRequestMockProvider;

    @MockitoSpyBean
    protected ExamLiveEventsService examLiveEventsService;

    @MockitoSpyBean
    protected GroupNotificationScheduleService groupNotificationScheduleService;

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        Mockito.reset(continuousIntegrationService);
        super.resetSpyBeans();
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject, boolean useCustomBuildPlanDefinition, boolean useCustomBuildPlanWorked)
            throws Exception {
        final var projectKey = exercise.getProjectKey();
        jenkinsRequestMockProvider.mockCreateProjectForExercise(exercise, failToCreateCiProject);
        if (useCustomBuildPlanDefinition) {
            aeolusRequestMockProvider.enableMockingOfRequests();
            if (useCustomBuildPlanWorked) {
                aeolusRequestMockProvider.mockSuccessfulPublishBuildPlan(AeolusTarget.JENKINS, projectKey + "-" + TEMPLATE.getName());
                aeolusRequestMockProvider.mockSuccessfulPublishBuildPlan(AeolusTarget.JENKINS, projectKey + "-" + SOLUTION.getName());
            }
            else {
                aeolusRequestMockProvider.mockFailedPublishBuildPlan(AeolusTarget.JENKINS);
                aeolusRequestMockProvider.mockFailedPublishBuildPlan(AeolusTarget.JENKINS);
                jenkinsRequestMockProvider.mockCreateBuildPlan(projectKey, TEMPLATE.getName(), false);
                jenkinsRequestMockProvider.mockCreateBuildPlan(projectKey, SOLUTION.getName(), false);
            }
            jenkinsRequestMockProvider.mockCreateCustomBuildPlan(projectKey, TEMPLATE.getName());
            jenkinsRequestMockProvider.mockCreateCustomBuildPlan(projectKey, SOLUTION.getName());

        }
        else {
            jenkinsRequestMockProvider.mockCreateBuildPlan(projectKey, TEMPLATE.getName(), false);
            jenkinsRequestMockProvider.mockCreateBuildPlan(projectKey, SOLUTION.getName(), false);
        }
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, TEMPLATE.getName(), false);
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, SOLUTION.getName(), false);
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos)
            throws Exception {
        if (!recreateBuildPlans) {
            mockCloneAndEnableAllBuildPlans(sourceExercise, exerciseToBeImported, true, false);
            mockUpdatePlanRepositoriesInBuildPlans(exerciseToBeImported);
        }
        else {
            mockSetupBuildPlansForNewExercise(exerciseToBeImported);
        }
        if (addAuxRepos) {
            // we basically mock the same requests for template and solution, in reality they would include a different payload (job.xml)
            mockUpdatePlanRepository(exerciseToBeImported, exerciseToBeImported.getProjectKey() + "-" + TEMPLATE.getName(), null, null);
            mockUpdatePlanRepository(exerciseToBeImported, exerciseToBeImported.getProjectKey() + "-" + SOLUTION.getName(), null, null);
        }
    }

    @Override
    public void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) throws Exception {
        mockConnectorRequestsForSetup(exerciseForImport, false, false, false);
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) throws Exception {
        mockCloneAndEnableAllBuildPlans(sourceExercise, exerciseToBeImported, planExistsInCi, shouldPlanEnableFail);
        mockUpdatePlanRepositoriesInBuildPlans(exerciseToBeImported);
    }

    private void mockCloneAndEnableAllBuildPlans(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi, boolean shouldPlanEnableFail)
            throws Exception {
        final var targetProjectKey = exerciseToBeImported.getProjectKey();
        String templateBuildPlanId = targetProjectKey + "-" + TEMPLATE.getName();
        String solutionBuildPlanId = targetProjectKey + "-" + SOLUTION.getName();

        jenkinsRequestMockProvider.mockGetFolderJob(targetProjectKey, null);
        jenkinsRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported, false);
        jenkinsRequestMockProvider.mockGetFolderJob(targetProjectKey);
        jenkinsRequestMockProvider.mockCopyBuildPlanFromTemplate(sourceExercise.getProjectKey(), targetProjectKey, templateBuildPlanId);
        jenkinsRequestMockProvider.mockCopyBuildPlanFromSolution(sourceExercise.getProjectKey(), targetProjectKey, solutionBuildPlanId);
        jenkinsRequestMockProvider.mockGivePlanPermissions(targetProjectKey, templateBuildPlanId);
        jenkinsRequestMockProvider.mockGivePlanPermissions(targetProjectKey, solutionBuildPlanId);
        jenkinsRequestMockProvider.mockEnablePlan(targetProjectKey, templateBuildPlanId, planExistsInCi, shouldPlanEnableFail);
        jenkinsRequestMockProvider.mockEnablePlan(targetProjectKey, solutionBuildPlanId, planExistsInCi, shouldPlanEnableFail);
    }

    private void mockUpdatePlanRepositoriesInBuildPlans(ProgrammingExercise exerciseToBeImported) throws Exception {
        String templateBuildPlanId = exerciseToBeImported.getProjectKey() + "-" + TEMPLATE.getName();
        String solutionBuildPlanId = exerciseToBeImported.getProjectKey() + "-" + SOLUTION.getName();

        final var targetTemplateRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TEMPLATE);
        final var targetSolutionRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.SOLUTION);
        final var targetTestsRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TESTS);

        mockUpdatePlanRepository(exerciseToBeImported, templateBuildPlanId, ASSIGNMENT_REPO_NAME, targetTemplateRepoName);
        mockUpdatePlanRepository(exerciseToBeImported, templateBuildPlanId, TEST_REPO_NAME, targetTestsRepoName);
        mockUpdatePlanRepository(exerciseToBeImported, solutionBuildPlanId, ASSIGNMENT_REPO_NAME, targetSolutionRepoName);
        mockUpdatePlanRepository(exerciseToBeImported, solutionBuildPlanId, TEST_REPO_NAME, targetTestsRepoName);
    }

    private void mockSetupBuildPlansForNewExercise(ProgrammingExercise exerciseToBeImported) throws Exception {
        final var targetProjectKey = exerciseToBeImported.getProjectKey();
        jenkinsRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported, false);
        jenkinsRequestMockProvider.mockCreateBuildPlan(targetProjectKey, TEMPLATE.getName(), false);
        jenkinsRequestMockProvider.mockCreateBuildPlan(targetProjectKey, SOLUTION.getName(), false);
        jenkinsRequestMockProvider.mockTriggerBuild(targetProjectKey, TEMPLATE.getName(), false);
        jenkinsRequestMockProvider.mockTriggerBuild(targetProjectKey, SOLUTION.getName(), false);
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception {
        // Step 2a)
        jenkinsRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        jenkinsRequestMockProvider.mockConfigureBuildPlan(exercise, username);
        // Note: Step 2c) is not needed in the Jenkins setup
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception {
        // Step 2a)
        jenkinsRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        jenkinsRequestMockProvider.mockConfigureBuildPlan(exercise, username);
        // Note: Step 2c) is not needed in the Jenkins setup
    }

    public void mockConnectorRequestsForStartPractice(ProgrammingExercise exercise, String username, Set<User> users) throws IOException, URISyntaxException {
        // Step 2a)
        jenkinsRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        // Note: no need to mock empty commit (Step 2c) because this is done on a git repository
        mockUpdatePlanRepositoryForParticipation(exercise, username);
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var repoName = projectKey.toLowerCase() + "-" + username;
        mockUpdatePlanRepository(exercise, username, ASSIGNMENT_REPO_NAME, repoName);
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs) throws IOException, URISyntaxException {
        jenkinsRequestMockProvider.mockUpdatePlanRepository(exercise.getProjectKey(), planName, false);
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        jenkinsRequestMockProvider.mockCopyBuildPlanFromTemplate(projectKey, projectKey, participation.getBuildPlanId());
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception {
        jenkinsRequestMockProvider.mockConfigureBuildPlan(participation.getProgrammingExercise(), participation.getParticipantIdentifier());
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());

        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        jenkinsRequestMockProvider.mockGetBuildStatus(projectKey, buildPlanId, true, false, false, false);

        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, buildPlanId, false);
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws Exception {
        final String slug = "test201904bprogrammingexercise6-exercise-testuser";
        final String hash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        final String projectKey = participation.getProgrammingExercise().getProjectKey();
        mockFetchCommitInfo(projectKey, slug, hash);
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, participation.getBuildPlanId(), false);
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        jenkinsRequestMockProvider.mockTriggerBuild(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), false);
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        jenkinsRequestMockProvider.mockTriggerBuild(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), false);
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) throws Exception {
        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), oldGroups, true);
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws Exception {
        jenkinsRequestMockProvider.mockCreateUser(user, userExistsInCi, false, false);
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) throws Exception {
        jenkinsRequestMockProvider.mockCreateUser(user, false, failInCi, failToGetCiUser);
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) throws Exception {
        jenkinsRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, false, false);
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) throws Exception {
        jenkinsRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, failToAddUsers, failToRemoveUsers);
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) throws Exception {
        jenkinsRequestMockProvider.mockDeleteBuildPlan(projectKey, planName, shouldFail);
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) throws Exception {
        jenkinsRequestMockProvider.mockDeleteBuildPlanProject(projectKey, shouldFail);
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) throws Exception {
        jenkinsRequestMockProvider.mockAddUsersToGroups(Set.of(group), failInCi);
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws Exception {
        jenkinsRequestMockProvider.mockRemoveUserFromGroups(Set.of(group), failInCi);
        jenkinsRequestMockProvider.mockAddUsersToGroups(Set.of(group), false);
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild)
            throws Exception {
        jenkinsRequestMockProvider.mockGetBuildStatus(projectKey, planName, planExistsInCi, planIsActive, planIsBuilding, failToGetBuild);
    }

    @Override
    public void mockGetBuildPlanConfig(String projectKey, String planName) throws Exception {
        jenkinsRequestMockProvider.mockGetFolderJob(projectKey);
        jenkinsRequestMockProvider.mockGetFolderConfig(projectKey);
        jenkinsRequestMockProvider.mockGetJobConfig(projectKey, planName);
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws Exception {
        jenkinsRequestMockProvider.mockHealth(isRunning, httpStatus);
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) throws Exception {
        jenkinsRequestMockProvider.mockCheckIfProjectExists(exercise, existsInCi, shouldFail);
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String buildPlanId, boolean buildPlanExists, boolean shouldFail) throws Exception {
        jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(projectKey, buildPlanId, buildPlanExists, shouldFail);
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws Exception {
        var projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
        var buildPlanId = programmingExerciseParticipation.getBuildPlanId();
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, buildPlanId, false);
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws Exception {
        var projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
        var buildPlanId = programmingExerciseParticipation.getBuildPlanId();
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, buildPlanId, true);
    }

    @Override
    public void resetMockProvider() throws Exception {
        jenkinsRequestMockProvider.reset();
    }

    /**
     * Configures the mock requests needed to delete a programming exercise in an exam.
     *
     * @param programmingExercise the programming exercise to delete
     * @param registeredUsers     the users registered to the exam (users with repos)
     * @throws Exception exception
     */
    public void mockDeleteProgrammingExercise(ProgrammingExercise programmingExercise, Set<User> registeredUsers) throws Exception {
        final String projectKey = programmingExercise.getProjectKey();

        List<String> studentLogins = registeredUsers.stream().map(User::getLogin).toList();
        jenkinsRequestMockProvider.mockDeleteBuildPlanProject(projectKey, false);
        List<String> planNames = new ArrayList<>(studentLogins);
        planNames.add(TEMPLATE.getName());
        planNames.add(SOLUTION.getName());
        for (final String planName : planNames) {
            jenkinsRequestMockProvider.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
    }

    @Override
    public void mockGetCiProjectMissing(ProgrammingExercise exercise) throws IOException {
        jenkinsRequestMockProvider.mockGetFolderJob(exercise.getProjectKey(), null);
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        // Not needed for this test
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) throws MalformedURLException, URISyntaxException, JsonProcessingException {
        // Not needed for this test
    }

    @Override
    public void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) throws URISyntaxException, JsonProcessingException {
        // Not needed for this test
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockDeleteRepository(String projectKey, String repositoryName, boolean shouldFail) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey, boolean shouldFail) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockRepositoryUriIsValid(VcsRepositoryUri vcsTemplateRepositoryUri, String projectKey, boolean b) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) throws IOException {
        // Not needed for this test
    }

    @Override
    public void mockUserExists(String username) throws Exception {
        // Not needed for this test
    }
}
