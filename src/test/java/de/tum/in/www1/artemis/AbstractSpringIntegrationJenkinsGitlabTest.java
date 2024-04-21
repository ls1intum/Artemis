package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.offbytwo.jenkins.JenkinsServer;

import de.tum.in.www1.artemis.connector.AeolusRequestMockProvider;
import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.connector.JenkinsRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabService;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsService;

@ResourceLock("AbstractSpringIntegrationJenkinsGitlabTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", PROFILE_CORE, "gitlab", "jenkins", "athena", "scheduling", "lti", "aeolus", "apollon" })
@TestPropertySource(properties = { "info.guided-tour.course-group-tutors=artemis-artemistutorial-tutors", "info.guided-tour.course-group-students=artemis-artemistutorial-students",
        "info.guided-tour.course-group-editors=artemis-artemistutorial-editors", "info.guided-tour.course-group-instructors=artemis-artemistutorial-instructors",
        "artemis.user-management.use-external=false", "artemis.user-management.course-enrollment.allowed-username-pattern=^(?!authorizationservicestudent2).*$" })
public abstract class AbstractSpringIntegrationJenkinsGitlabTest extends AbstractArtemisIntegrationTest {

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Jenkins using the corresponding RestTemplate.
    @SpyBean
    protected JenkinsService continuousIntegrationService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Gitlab using the corresponding RestTemplate and
    // GitlabApi.
    @SpyBean
    protected GitLabService versionControlService;

    @SpyBean
    protected JenkinsServer jenkinsServer;

    @Autowired
    protected JenkinsRequestMockProvider jenkinsRequestMockProvider;

    @Autowired
    protected GitlabRequestMockProvider gitlabRequestMockProvider;

    @Autowired
    protected AeolusRequestMockProvider aeolusRequestMockProvider;

    @AfterEach
    protected void resetSpyBeans() {
        Mockito.reset(continuousIntegrationService, versionControlService, jenkinsServer);
        super.resetSpyBeans();
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject, boolean useCustomBuildPlanDefinition, boolean useCustomBuildPlanWorked)
            throws Exception {
        final var projectKey = exercise.getProjectKey();
        final var exerciseRepoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = exercise.generateRepositoryName(RepositoryType.TESTS);
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        gitlabRequestMockProvider.mockCreateProjectForExercise(exercise);
        gitlabRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
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
        mockImportRepositories(exerciseToBeImported);
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
        mockImportRepositories(exerciseToBeImported);
        mockCloneAndEnableAllBuildPlans(sourceExercise, exerciseToBeImported, planExistsInCi, shouldPlanEnableFail);
        mockUpdatePlanRepositoriesInBuildPlans(exerciseToBeImported);
    }

    private void mockImportRepositories(ProgrammingExercise exerciseToBeImported) throws Exception {
        final var targetTemplateRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TEMPLATE);
        final var targetSolutionRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.SOLUTION);
        final var targetTestsRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TESTS);

        gitlabRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false);

        gitlabRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetTemplateRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetSolutionRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetTestsRepoName);
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
    }

    private void mockCloneAndEnableAllBuildPlans(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi, boolean shouldPlanEnableFail)
            throws Exception {
        final var targetProjectKey = exerciseToBeImported.getProjectKey();
        String templateBuildPlanId = targetProjectKey + "-" + TEMPLATE.getName();
        String solutionBuildPlanId = targetProjectKey + "-" + SOLUTION.getName();

        jenkinsRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported, false);
        jenkinsRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), targetProjectKey);
        jenkinsRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), targetProjectKey);
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
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws GitLabApiException {
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception {
        // Step 1a)
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        // Step 1c)
        gitlabRequestMockProvider.mockConfigureRepository(exercise, users, ltiUserExists);
        // Step 2a)
        jenkinsRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        jenkinsRequestMockProvider.mockConfigureBuildPlan(exercise, username);
        // Note: Step 2c) is not needed in the Jenkins setup
        // Step 1c)
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception {
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        // Step 2a)
        jenkinsRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        jenkinsRequestMockProvider.mockConfigureBuildPlan(exercise, username);
        // Note: Step 2c) is not needed in the Jenkins setup
    }

    public void mockConnectorRequestsForStartPractice(ProgrammingExercise exercise, String username, Set<User> users) throws IOException, URISyntaxException, GitLabApiException {
        // Step 1a)
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        // Step 1c)
        gitlabRequestMockProvider.mockConfigureRepository(exercise, users, true);
        // Step 2a)
        jenkinsRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        // Note: no need to mock empty commit (Step 2c) because this is done on a git repository
        mockUpdatePlanRepositoryForParticipation(exercise, username);

        // Mock Default Branch
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
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
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws Exception {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        gitlabRequestMockProvider.mockRemoveMemberFromRepository(repositorySlug, firstStudent.getLogin());
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws Exception {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        final var repositoryPath = exercise.getProjectKey() + "/" + repositorySlug;
        gitlabRequestMockProvider.mockAddMemberToRepository(repositoryPath, newStudent.getLogin(), !status.is2xxSuccessful());
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) throws Exception {
        final var repositorySlug = (exercise.getProjectKey() + "-" + student.getParticipantIdentifier()).toLowerCase();
        final var repositoryPath = exercise.getProjectKey() + "/" + repositorySlug;
        gitlabRequestMockProvider.mockAddMemberToRepository(repositoryPath, student.getLogin(), !status.is2xxSuccessful());
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) {
        // Not necessary for the core functionality
    }

    @Override
    public void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) {
        // Not needed in Gitlab
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        jenkinsRequestMockProvider.mockCopyBuildPlan(projectKey, projectKey);
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
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) {
        // Not needed here
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
        gitlabRequestMockProvider.mockUpdateVcsUser(oldLogin, user, oldGroups, user.getGroups(), true);
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws Exception {
        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        jenkinsRequestMockProvider.mockCreateUser(user, userExistsInCi, false, false);
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) throws Exception {
        gitlabRequestMockProvider.mockCreateVcsUser(user, failInVcs);
        jenkinsRequestMockProvider.mockCreateUser(user, false, failInCi, failToGetCiUser);
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) throws Exception {
        gitlabRequestMockProvider.mockDeleteVcsUser(user.getLogin(), userExistsInUserManagement, failInVcs);
        jenkinsRequestMockProvider.mockDeleteUser(user, userExistsInUserManagement, failInCi);
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) throws Exception {
        gitlabRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
        jenkinsRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, false, false);
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) throws Exception {
        gitlabRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
        jenkinsRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, failToAddUsers, failToRemoveUsers);
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) {
        // Not needed here
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) {
        // Not needed here
    }

    @Override
    public void mockDeleteRepository(String projectKey, String repositoryName, boolean shouldFail) throws Exception {
        gitlabRequestMockProvider.mockDeleteRepository(projectKey + "/" + repositoryName, shouldFail);
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey, boolean shouldFail) throws Exception {
        gitlabRequestMockProvider.mockDeleteProject(projectKey, shouldFail);
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
        gitlabRequestMockProvider.mockUpdateVcsUser(user.getLogin(), user, Set.of(), Set.of(group), false);
        jenkinsRequestMockProvider.mockAddUsersToGroups(user.getLogin(), Set.of(group), failInCi);
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws Exception {
        gitlabRequestMockProvider.mockUpdateVcsUser(user.getLogin(), user, Set.of(group), Set.of(), false);
        jenkinsRequestMockProvider.mockRemoveUserFromGroups(Set.of(group), failInCi);
        jenkinsRequestMockProvider.mockAddUsersToGroups(user.getLogin(), Set.of(group), false);
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild)
            throws Exception {
        jenkinsRequestMockProvider.mockGetBuildStatus(projectKey, planName, planExistsInCi, planIsActive, planIsBuilding, failToGetBuild);
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws Exception {
        jenkinsRequestMockProvider.mockHealth(isRunning, httpStatus);
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        // Not needed here
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) throws Exception {
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, existsInVcs);
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) throws Exception {
        jenkinsRequestMockProvider.mockCheckIfProjectExists(exercise, existsInCi, shouldFail);
    }

    @Override
    public void mockRepositoryUriIsValid(VcsRepositoryUri repositoryUri, String projectKey, boolean isUrlValid) throws Exception {
        gitlabRequestMockProvider.mockRepositoryUriIsValid(repositoryUri, isUrlValid);
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
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUri repositoryUri, String projectKey, Set<User> users) throws Exception {
        gitlabRequestMockProvider.setRepositoryPermissionsToReadOnly(repositoryUri, users);
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) throws Exception {
        gitlabRequestMockProvider.mockConfigureRepository(exercise, students, userExists);
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
    }

    @Override
    public void resetMockProvider() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
    }

    @Override
    public void verifyMocks() {
        gitlabRequestMockProvider.verifyMocks();
        jenkinsRequestMockProvider.verifyMocks();
    }

    @Override
    public void mockUserExists(String username) throws Exception {
        gitlabRequestMockProvider.mockUserExists(username, true);
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
        List<String> repoNames = new ArrayList<>(studentLogins);

        for (final var repoType : RepositoryType.values()) {
            gitlabRequestMockProvider.mockDeleteRepository(programmingExercise.generateRepositoryName(repoType), false);
        }

        for (final var repoName : repoNames) {
            gitlabRequestMockProvider.mockDeleteRepository((projectKey + "-" + repoName).toLowerCase(), false);
        }
        gitlabRequestMockProvider.mockDeleteProject(projectKey, false);
    }
}
