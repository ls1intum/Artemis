package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static org.mockito.Mockito.*;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.atlassian.bamboo.specs.api.exceptions.BambooSpecsPublishingException;
import com.atlassian.bamboo.specs.util.BambooServer;
import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.connector.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.connector.JiraRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.TimeService;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooResultService;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooService;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooTriggerService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildPlanDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooRepositoryDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BambooBuildPlanUpdateService;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketService;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("AbstractSpringIntegrationBambooBitbucketJiraTest")
@AutoConfigureEmbeddedDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "bamboo", "bitbucket", "jira", "ldap", "scheduling", "athena", "apollon", "iris" })
public abstract class AbstractSpringIntegrationBambooBitbucketJiraTest extends AbstractArtemisIntegrationTest {

    @SpyBean
    protected TimeService timeService;

    @SpyBean
    protected LdapUserService ldapUserService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Bamboo using the corresponding RestTemplate.
    @SpyBean
    protected BambooBuildPlanUpdateService continuousIntegrationUpdateService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Bamboo using the corresponding RestTemplate.
    @SpyBean
    protected BambooService continuousIntegrationService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Bamboo using the corresponding RestTemplate.
    @SpyBean
    protected BambooResultService continuousIntegrationResultService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Bamboo using the corresponding RestTemplate.
    @SpyBean
    protected BambooTriggerService continuousIntegrationTriggerService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Bitbucket using the corresponding RestTemplate.
    @SpyBean
    protected BitbucketService versionControlService;

    @SpyBean
    protected BambooServer bambooServer;

    @Autowired
    protected BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    protected BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    protected JiraRequestMockProvider jiraRequestMockProvider;

    @Autowired
    protected PasswordService passwordService;

    @AfterEach
    protected void resetSpyBeans() {
        Mockito.reset(ldapUserService, continuousIntegrationUpdateService, continuousIntegrationService, versionControlService, bambooServer, textBlockService);
        super.resetSpyBeans();
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        bitbucketRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists)
            throws IOException, URISyntaxException {
        // Step 1a)
        bitbucketRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        // Step 1c)
        bitbucketRequestMockProvider.mockConfigureRepository(exercise, username, users, ltiUserExists);
        // Step 2a)
        bambooRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        // Note: no need to mock empty commit (Step 2c) because this is done on a git repository
        mockUpdatePlanRepositoryForParticipation(exercise, username);
        // Step 1c)
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);

        // Mock Default Branch
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, exercise.getProjectKey());
    }

    public void mockConnectorRequestsForStartPractice(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists)
            throws IOException, URISyntaxException {
        // Step 1a)
        bitbucketRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username, true);
        // Step 1c)
        bitbucketRequestMockProvider.mockConfigureRepository(exercise, username, users, ltiUserExists, true);
        // Step 2a)
        bambooRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username, true);
        // Step 2b)
        // Note: no need to mock empty commit (Step 2c) because this is done on a git repository
        mockUpdatePlanRepositoryForParticipation(exercise, username, true);
        // Step 1c)
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);

        // Mock Default Branch
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, exercise.getProjectKey());
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists)
            throws IOException, URISyntaxException {
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, exercise.getProjectKey());
        // Step 2a)
        bambooRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        mockUpdatePlanRepositoryForParticipation(exercise, username);
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException {
        mockUpdatePlanRepositoryForParticipation(exercise, username, false);
    }

    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username, boolean practiceMode) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var bitbucketRepoName = projectKey.toLowerCase() + "-" + (practiceMode ? "practice-" : "") + username;
        mockUpdatePlanRepository(exercise, username, ASSIGNMENT_REPO_NAME, bitbucketRepoName);
        bambooRequestMockProvider.mockEnablePlan(exercise.getProjectKey(), username, true, false);
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var buildPlanKey = (projectKey + "-" + planName).toUpperCase();

        final var bambooRepositoryAssignment = new BambooRepositoryDTO(296200357L, ASSIGNMENT_REPO_NAME);
        final var bambooRepositoryTests = new BambooRepositoryDTO(296200356L, TEST_REPO_NAME);
        final var bambooRepositoryAuxRepo = new BambooRepositoryDTO(296200358L, "auxrepo");
        final var newRepoUrl = versionControlService.getCloneRepositoryUrl(exercise.getProjectKey(), repoNameInVcs).toString();

        bambooRequestMockProvider.mockGetBuildPlanRepositoryList(buildPlanKey);

        if (ASSIGNMENT_REPO_NAME.equals(repoNameInCI)) {
            bambooRequestMockProvider.mockUpdateRepository(buildPlanKey, bambooRepositoryAssignment, newRepoUrl, defaultBranch);
        }
        else if (TEST_REPO_NAME.equals(repoNameInCI)) {
            bambooRequestMockProvider.mockUpdateRepository(buildPlanKey, bambooRepositoryTests, newRepoUrl, defaultBranch);
        }
        else if ("auxrepo".equals(repoNameInCI)) {
            bambooRequestMockProvider.mockUpdateRepository(buildPlanKey, bambooRepositoryAuxRepo, newRepoUrl, defaultBranch);
        }
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject) throws Exception {
        final var exerciseRepoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = exercise.generateRepositoryName(RepositoryType.TESTS);
        final var templateBuildPlanId = exercise.generateBuildPlanId(TEMPLATE);
        final var solutionBuildPlanId = exercise.generateBuildPlanId(SOLUTION);
        bambooRequestMockProvider.mockCheckIfProjectExists(exercise, false, false);
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exercise);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, exercise.getProjectKey());
        for (var auxiliaryRepository : exercise.getAuxiliaryRepositories()) {
            final var auxiliaryRepoName = exercise.generateRepositoryName(auxiliaryRepository.getName());
            bitbucketRequestMockProvider.mockCreateRepository(exercise, auxiliaryRepoName);
        }
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        mockBambooBuildPlanCreation(exercise, failToCreateCiProject, templateBuildPlanId, solutionBuildPlanId);
    }

    private void mockBambooBuildPlanCreation(ProgrammingExercise exercise, boolean failToCreateCiProject, String templateBuildPlanId, String solutionBuildPlanId)
            throws IOException, URISyntaxException {
        if (!failToCreateCiProject) {
            // TODO: check the actual plan and plan permissions that get passed here
            doReturn(null).when(bambooServer).publish(any());
            bambooRequestMockProvider.mockRemoveAllDefaultProjectPermissions(exercise);
            bambooRequestMockProvider.mockGiveProjectPermissions(exercise);

            bambooRequestMockProvider.mockTriggerBuild(templateBuildPlanId);
            bambooRequestMockProvider.mockTriggerBuild(solutionBuildPlanId);
        }
        else {
            doThrow(BambooSpecsPublishingException.class).when(bambooServer).publish(any());
        }
    }

    @Override
    public void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) throws Exception {
        mockConnectorRequestsForSetup(exerciseForImport, false);

    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos)
            throws Exception {

        mockImportRepositories(sourceExercise, exerciseToBeImported);

        bambooRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false, false);

        String templateBuildPlanId = exerciseToBeImported.generateBuildPlanId(TEMPLATE);
        String solutionBuildPlanId = exerciseToBeImported.generateBuildPlanId(SOLUTION);
        if (!recreateBuildPlans) {
            mockCloneAndEnableAllBuildPlans(sourceExercise, exerciseToBeImported, true, false);
        }
        else {
            // Mocks for recreating the build plans
            mockBambooBuildPlanCreation(exerciseToBeImported, false, templateBuildPlanId, solutionBuildPlanId);
        }
        if (addAuxRepos) {
            mockUpdatePlanRepository(exerciseToBeImported, "auxrepo", "auxrepo", "auxrepo");
        }
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) throws Exception {
        mockImportRepositories(sourceExercise, exerciseToBeImported);
        bambooRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false, false);
        mockCloneAndEnableAllBuildPlans(sourceExercise, exerciseToBeImported, planExistsInCi, shouldPlanEnableFail);
    }

    private void mockImportRepositories(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported) throws Exception {
        final var projectKey = exerciseToBeImported.getProjectKey();
        var exerciseRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TEMPLATE);
        var solutionRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.SOLUTION);
        var testRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TESTS);

        // take the latest participationId because we assume that it increases in the database for the participations in the imported exercises
        var nextParticipationId = Math.max(sourceExercise.getSolutionParticipation().getId(), sourceExercise.getTemplateParticipation().getId()) + 1;
        final var artemisTemplateHookPath = artemisServerUrl + "/api/public/programming-submissions/" + nextParticipationId++;
        final var artemisSolutionHookPath = artemisServerUrl + "/api/public/programming-submissions/" + nextParticipationId;
        final var artemisTestsHookPath = artemisServerUrl + "/api/public/programming-exercises/test-cases-changed/" + (sourceExercise.getId() + 1);

        bitbucketRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported);
        bitbucketRequestMockProvider.mockCreateRepository(exerciseToBeImported, exerciseRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exerciseToBeImported, solutionRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exerciseToBeImported, testRepoName);
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, exerciseToBeImported.getProjectKey());
        for (AuxiliaryRepository repository : sourceExercise.getAuxiliaryRepositories()) {
            final var auxRepoName = exerciseToBeImported.generateRepositoryName(repository.getName());
            bitbucketRequestMockProvider.mockCreateRepository(exerciseToBeImported, auxRepoName);
        }
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, exerciseRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, exerciseRepoName, artemisTemplateHookPath);
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, solutionRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, solutionRepoName, artemisSolutionHookPath);
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, testRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, testRepoName, artemisTestsHookPath);
    }

    private void mockCloneAndEnableAllBuildPlans(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi, boolean shouldPlanEnableFail)
            throws Exception {
        final var projectKey = exerciseToBeImported.getProjectKey();
        final var templateRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.SOLUTION);
        final var testsRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TESTS);

        bambooRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), TEMPLATE.getName(), projectKey, TEMPLATE.getName(), false);
        bambooRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), SOLUTION.getName(), projectKey, SOLUTION.getName(), true);
        // TODO: Mock continuousIntegrationService.givePlanPermissions for Template and Solution plan
        doReturn(null).when(bambooServer).publish(any());
        bambooRequestMockProvider.mockGiveProjectPermissions(exerciseToBeImported);
        bambooRequestMockProvider.mockEnablePlan(projectKey, TEMPLATE.getName(), planExistsInCi, shouldPlanEnableFail);
        bambooRequestMockProvider.mockEnablePlan(projectKey, SOLUTION.getName(), planExistsInCi, shouldPlanEnableFail);
        mockUpdatePlanRepository(exerciseToBeImported, TEMPLATE.getName(), ASSIGNMENT_REPO_NAME, templateRepoName);
        mockUpdatePlanRepository(exerciseToBeImported, TEMPLATE.getName(), TEST_REPO_NAME, testsRepoName);
        for (AuxiliaryRepository repository : sourceExercise.getAuxiliaryRepositories()) {
            final var auxRepoName = exerciseToBeImported.generateRepositoryName(repository.getName());
            mockUpdatePlanRepository(exerciseToBeImported, TEMPLATE.getName(), repository.getName(), auxRepoName);
            mockUpdatePlanRepository(exerciseToBeImported, SOLUTION.getName(), repository.getName(), auxRepoName);
        }
        mockUpdatePlanRepository(exerciseToBeImported, SOLUTION.getName(), ASSIGNMENT_REPO_NAME, solutionRepoName);
        mockUpdatePlanRepository(exerciseToBeImported, SOLUTION.getName(), TEST_REPO_NAME, testsRepoName);
        bambooRequestMockProvider.mockTriggerBuild(projectKey + "-" + TEMPLATE.getName());
        bambooRequestMockProvider.mockTriggerBuild(projectKey + "-" + SOLUTION.getName());
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws URISyntaxException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        bitbucketRequestMockProvider.mockRemoveMemberFromRepository(repositorySlug, exercise.getProjectKey(), firstStudent);
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws URISyntaxException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        bitbucketRequestMockProvider.mockGiveWritePermission(exercise, repositorySlug, newStudent.getLogin(), status);
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) throws URISyntaxException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + student.getParticipantIdentifier()).toLowerCase();
        bitbucketRequestMockProvider.mockGiveWritePermission(exercise, repositorySlug, student.getLogin(), status);
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) throws MalformedURLException, URISyntaxException, JsonProcessingException {
        // prepare the build result
        bambooRequestMockProvider.mockQueryLatestBuildResultFromBambooServer(participation.getBuildPlanId());
        // prepare the artifact to be null
        bambooRequestMockProvider.mockRetrieveEmptyArtifactPage();
    }

    @Override
    public void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) throws URISyntaxException, JsonProcessingException {
        bitbucketRequestMockProvider.mockFetchCommitInfo(projectKey, repositorySlug, hash);
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planName = BuildPlanType.TEMPLATE.getName();
        final var username = participation.getParticipantIdentifier();
        bambooRequestMockProvider.mockCopyBuildPlan(projectKey, planName, projectKey, username.toUpperCase(), true);
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception {
        final var buildPlanId = participation.getBuildPlanId();
        final var repositoryUrl = participation.getVcsRepositoryUrl();
        final var planKey = participation.getBuildPlanId();
        bambooRequestMockProvider.mockUpdatePlanRepository(planKey, ASSIGNMENT_REPO_NAME, repositoryUrl.toString(), defaultBranch);

        // Isn't mockEnablePlan() written incorrectly since projectKey isn't even used by the bamboo service?
        var splitted = buildPlanId.split("-");
        bambooRequestMockProvider.mockEnablePlan(splitted[0], splitted[1], true, false);
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        String buildPlanId = participation.getBuildPlanId();
        bambooRequestMockProvider.mockGetBuildPlan(buildPlanId, buildPlanId != null ? new BambooBuildPlanDTO(null) : null, false);
        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        bambooRequestMockProvider.mockTriggerBuild(participation);
    }

    @Override
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) throws URISyntaxException {
        for (User user : participation.getParticipant().getParticipants()) {
            String buildPlanId = participation.getBuildPlanId();
            String projectKey = buildPlanId.split("-")[0];

            bambooRequestMockProvider.mockGrantReadAccess(buildPlanId, projectKey, user);
        }
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws Exception {
        final String slug = "test201904bprogrammingexercise6-exercise-testuser";
        final String hash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        mockFetchCommitInfo(participation.getProgrammingExercise().getProjectKey(), slug, hash);
        bambooRequestMockProvider.mockTriggerBuild(participation);
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        bambooRequestMockProvider.mockTriggerBuild(participation);
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        bambooRequestMockProvider.mockTriggerBuild(participation);
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) throws JsonProcessingException, URISyntaxException {
        var managedUserVM = new ManagedUserVM(user);
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(managedUserVM.getGroups());
        jiraRequestMockProvider.mockRemoveUserFromGroup(oldGroups, managedUserVM.getLogin(), false, true);
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(managedUserVM.getGroups());

        bitbucketRequestMockProvider.mockUpdateUserDetails(oldLogin, user.getEmail(), user.getFirstName() + " " + user.getLastName());
        if (password != null) {
            bitbucketRequestMockProvider.mockUpdateUserPassword(user.getLogin(), password, true, true);
        }
        Set<String> groupsToAdd = new HashSet<>(user.getGroups());
        groupsToAdd.removeAll(oldGroups);
        Set<String> groupsToRemove = new HashSet<>(oldGroups);
        groupsToRemove.removeAll(user.getGroups());
        if (!groupsToAdd.isEmpty()) {
            bitbucketRequestMockProvider.mockAddUserToGroups();
        }
        for (String group : groupsToRemove) {
            bitbucketRequestMockProvider.mockRemoveUserFromGroup(user.getLogin(), group);
        }
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) {
        var managedUserVM = new ManagedUserVM(user);
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(managedUserVM.getGroups());
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(managedUserVM.getGroups());
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) {
        // Not needed here
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) {
        // User management and CI not needed here
        bitbucketRequestMockProvider.mockDeleteUser(user.getLogin(), failInVcs);
        if (!failInVcs) {
            bitbucketRequestMockProvider.mockEraseDeletedUser(user.getLogin());
        }
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        // Not needed here.
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) {
        // Not needed here
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) throws Exception {
        jiraRequestMockProvider.mockCreateGroup(groupName);
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) throws Exception {
        jiraRequestMockProvider.mockDeleteGroup(groupName);
    }

    @Override
    public void mockDeleteRepository(String projectKey, String repositoryName, boolean shouldFail) throws Exception {
        bitbucketRequestMockProvider.mockDeleteRepository(projectKey, repositoryName, shouldFail);
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey, boolean shouldFail) throws Exception {
        bitbucketRequestMockProvider.mockDeleteProject(projectKey, shouldFail);
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) throws Exception {
        bambooRequestMockProvider.mockDeleteBambooBuildPlan(planName, !shouldFail);
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) throws Exception {
        bambooRequestMockProvider.mockDeleteBambooBuildProject(projectKey);
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) {
        jiraRequestMockProvider.mockAddUserToGroup(group, failInCi);
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) {
        jiraRequestMockProvider.mockRemoveUserFromGroup(Set.of(group), user.getLogin(), failInCi, true);
    }

    @Override
    public void mockGetBuildPlan(String porjectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild)
            throws Exception {
        var buildPlanToReturn = planExistsInCi || failToGetBuild ? new BambooBuildPlanDTO(planIsActive, planIsBuilding) : null;
        bambooRequestMockProvider.mockGetBuildPlan(planName, buildPlanToReturn, failToGetBuild);
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws Exception {
        var state = isRunning ? "RUNNING" : "PAUSED";
        bambooRequestMockProvider.mockHealth(state, httpStatus);
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) throws Exception {
        // Make sure that all REST calls are necessary
        String buildPlanId = participation.getBuildPlanId();
        VcsRepositoryUrl repositoryUrl = participation.getVcsRepositoryUrl();
        String projectKey = buildPlanId.split("-")[0];
        bambooRequestMockProvider.mockUpdatePlanRepository(buildPlanId, "assignment", repositoryUrl.toString(), defaultBranch);
        bambooRequestMockProvider.mockEnablePlan(projectKey, buildPlanId.split("-")[1], true, false);
        if (Boolean.TRUE.equals(participation.getProgrammingExercise().isPublishBuildPlanUrl())) {
            for (User user : ((StudentParticipation) participation).getParticipant().getParticipants()) {
                bambooRequestMockProvider.mockGrantReadAccess(buildPlanId, projectKey, user);
            }
        }
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) throws Exception {
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exercise, existsInVcs);
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) throws Exception {
        bambooRequestMockProvider.mockCheckIfProjectExists(exercise, existsInCi, shouldFail);
    }

    @Override
    public void mockRepositoryUrlIsValid(VcsRepositoryUrl vcsTemplateRepositoryUrl, String projectKey, boolean b) throws Exception {
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(vcsTemplateRepositoryUrl, projectKey, b);
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) throws Exception {
        bambooRequestMockProvider.mockBuildPlanExists(templateBuildPlanId, buildPlanExists, shouldFail);
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws Exception {
        bambooRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation);
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws Exception {
        final var buildPlan = programmingExerciseParticipation.getBuildPlanId();
        bambooRequestMockProvider.mockTriggerBuildFailed(buildPlan);
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws Exception {
        var repositorySlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(repositorySlug, projectKey, users);
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) throws Exception {
        bitbucketRequestMockProvider.mockConfigureRepository(exercise, participantIdentifier, students, userExists);
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) throws IOException {
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, programmingExercise.getProjectKey());
    }

    @Override
    public void resetMockProvider() {
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
    }

    @Override
    public void verifyMocks() {
        bitbucketRequestMockProvider.verifyMocks();
        bambooRequestMockProvider.verifyMocks();
    }

    @Override
    public void mockUserExists(String username) throws Exception {
        bitbucketRequestMockProvider.mockUserExists(username);
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

        List<String> studentLogins = new ArrayList<>();
        for (final User user : registeredUsers) {
            studentLogins.add(user.getLogin());
        }
        bambooRequestMockProvider.mockDeleteBambooBuildProject(projectKey);
        List<String> planNames = new ArrayList<>(studentLogins);
        planNames.add(TEMPLATE.getName());
        planNames.add(SOLUTION.getName());
        for (final String planName : planNames) {
            bambooRequestMockProvider.mockDeleteBambooBuildPlan(projectKey + "-" + planName.toUpperCase(), false);
        }
        List<String> repoNames = new ArrayList<>(studentLogins);

        for (final var repoType : RepositoryType.values()) {
            bitbucketRequestMockProvider.mockDeleteRepository(projectKey, programmingExercise.generateRepositoryName(repoType), false);
        }

        for (final var repoName : repoNames) {
            bitbucketRequestMockProvider.mockDeleteRepository(projectKey, (projectKey + "-" + repoName).toLowerCase(), false);
        }
        bitbucketRequestMockProvider.mockDeleteProject(projectKey, false);
    }
}
