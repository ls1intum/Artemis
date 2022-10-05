package de.tum.in.www1.artemis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.PipelineStatus;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabService;
import de.tum.in.www1.artemis.service.connectors.gitlabci.GitLabCIService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;

@SpringBootTest(properties = { "artemis.athene.token-validity-in-seconds=10800",
        "artemis.athene.base64-secret=YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=" })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "gitlabci", "gitlab", "saml2", "scheduling" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationGitlabCIGitlabSamlTest extends AbstractArtemisIntegrationTest {

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Gitlab using the corresponding RestTemplate and
    // GitlabApi.
    @SpyBean
    protected GitLabCIService continuousIntegrationService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Gitlab using the corresponding RestTemplate and
    // GitlabApi.
    @SpyBean
    protected GitLabService versionControlService;

    @SpyBean
    protected GitLabApi gitlab;

    @Autowired
    protected PasswordService passwordService;

    @Autowired
    protected GitlabRequestMockProvider gitlabRequestMockProvider;

    // NOTE: this has to be a MockBean, because the class cannot be instantiated in the tests
    @MockBean
    protected RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    @AfterEach
    public void resetSpyBeans() {
        Mockito.reset(continuousIntegrationService, versionControlService, relyingPartyRegistrationRepository, mailService, gitlab);
        super.resetSpyBeans();
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject) throws Exception {
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

        if (failToCreateCiProject) {
            doThrow(new ContinuousIntegrationException()).when(continuousIntegrationService).createProjectForExercise(any());
            doThrow(new ContinuousIntegrationException()).when(continuousIntegrationService).createBuildPlanForExercise(any(), any(), any(), any(), any());
        }

        doNothing().when(gitService).pushSourceToTargetRepo(any(), any());

        // saml2-specific mocks
        doReturn(null).when(relyingPartyRegistrationRepository).findByRegistrationId(anyString());
        doNothing().when(mailService).sendSAML2SetPasswordMail(any(User.class));
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos)
            throws Exception {
        mockImportRepositories(exerciseToBeImported);
        doNothing().when(gitService).pushSourceToTargetRepo(any(), any());
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) throws Exception {
        mockImportRepositories(exerciseToBeImported);
        doNothing().when(gitService).pushSourceToTargetRepo(any(), any());
    }

    private void mockImportRepositories(ProgrammingExercise exerciseToBeImported) throws GitLabApiException {
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

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws GitLabApiException {
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists, HttpStatus status)
            throws GitLabApiException {
        // Step 1a)
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        // Step 1b)
        gitlabRequestMockProvider.mockConfigureRepository(exercise, users, ltiUserExists);
        // Step 1c)
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        // Step 2 is not needed in the GitLab CI setup.
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        // Step 2 is not needed in the GitLab CI setup.
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws GitLabApiException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        gitlabRequestMockProvider.mockRemoveMemberFromRepository(repositorySlug, firstStudent.getLogin());
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws GitLabApiException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        final var repositoryPath = exercise.getProjectKey() + "/" + repositorySlug;
        gitlabRequestMockProvider.mockAddMemberToRepository(repositoryPath, newStudent.getLogin(), !status.is2xxSuccessful());
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) throws GitLabApiException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + student.getParticipantIdentifier()).toLowerCase();
        final var repositoryPath = exercise.getProjectKey() + "/" + repositorySlug;
        gitlabRequestMockProvider.mockAddMemberToRepository(repositoryPath, student.getLogin(), !status.is2xxSuccessful());
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) {
        // Not necessary for the core functionality
    }

    @Override
    public void mockGetBuildLogs(ProgrammingExerciseStudentParticipation participation, List<BambooBuildResultDTO.BambooBuildLogEntryDTO> logs) {
        // TODO: implement
    }

    @Override
    public void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockAddBuildPlanToGitLabRepositoryConfiguration(false);
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws GitLabApiException {
        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
    }

    @Override
    public void mockFailToCreateUserInExernalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) throws GitLabApiException {
        gitlabRequestMockProvider.mockCreateVcsUser(user, failInVcs);
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) throws GitLabApiException {
        gitlabRequestMockProvider.mockDeleteVcsUser(user.getLogin(), userExistsInUserManagement, failInVcs);
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) throws GitLabApiException {
        gitlabRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) throws GitLabApiException {
        gitlabRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockDeleteRepository(String projectKey, String repostoryName, boolean shouldFail) throws GitLabApiException {
        gitlabRequestMockProvider.mockDeleteRepository(projectKey + "/" + repostoryName, shouldFail);
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey, boolean shouldFail) throws GitLabApiException {
        gitlabRequestMockProvider.mockDeleteProject(projectKey, shouldFail);
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) throws GitLabApiException {
        gitlabRequestMockProvider.mockUpdateVcsUser(user.getLogin(), user, Set.of(), Set.of(group), false);
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws GitLabApiException {
        gitlabRequestMockProvider.mockUpdateVcsUser(user.getLogin(), user, Set.of(group), Set.of(), false);
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth(isRunning ? "ok" : "notok", httpStatus);
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) throws GitLabApiException {
        mockAddBuildPlanToGitLabRepositoryConfiguration(false);
    }

    public void mockAddBuildPlanToGitLabRepositoryConfiguration(boolean shouldFail) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetProject(shouldFail);
        gitlabRequestMockProvider.mockUpdateProject(shouldFail);
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) throws GitLabApiException {
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, existsInVcs);
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockRepositoryUrlIsValid(VcsRepositoryUrl repositoryUrl, String projectKey, boolean isUrlValid) throws GitLabApiException {
        gitlabRequestMockProvider.mockRepositoryUrlIsValid(repositoryUrl, isUrlValid);
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String buildPlanId, boolean buildPlanExists, boolean shouldFail) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws GitLabApiException {
        mockTriggerBuild(true);
    }

    private void mockTriggerBuild(boolean shouldFail) throws GitLabApiException {
        gitlabRequestMockProvider.mockCreateTrigger(shouldFail);
        gitlabRequestMockProvider.mockTriggerPipeline(shouldFail);
        gitlabRequestMockProvider.mockDeleteTrigger(shouldFail);
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws GitLabApiException {
        gitlabRequestMockProvider.setRepositoryPermissionsToReadOnly(repositoryUrl, users);
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) throws GitLabApiException {
        gitlabRequestMockProvider.mockConfigureRepository(exercise, students, userExists);
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
    }

    public void mockGetBuildStatus(PipelineStatus pipelineStatus) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetBuildStatus(pipelineStatus);
    }

    @Override
    public void resetMockProvider() {
        gitlabRequestMockProvider.reset();
    }

    @Override
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) throws URISyntaxException {
        // Not needed here.
    }

    @Override
    public void verifyMocks() {
        gitlabRequestMockProvider.verifyMocks();
    }
}
