package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static io.github.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.offbytwo.jenkins.JenkinsServer;

import de.tum.in.www1.artemis.connector.gitlab.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.connector.jenkins.JenkinsRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabService;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsService;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;

@SpringBootTest(properties = { "artemis.athene.token-validity-in-seconds=10800",
        "artemis.athene.base64-secret=YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=" })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!

@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "gitlab", "jenkins", "athene", "scheduling" })
@TestPropertySource(properties = { "info.guided-tour.course-group-tutors=", "info.guided-tour.course-group-students=artemis-artemistutorial-students",
        "info.guided-tour.course-group-instructors=artemis-artemistutorial-instructors", "artemis.user-management.use-external=false" })

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

    @AfterEach
    public void resetSpyBeans() {
        Mockito.reset(continuousIntegrationService, versionControlService, jenkinsServer);
        super.resetSpyBeans();
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws Exception {
        final var projectKey = exercise.getProjectKey();
        final var exerciseRepoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = exercise.generateRepositoryName(RepositoryType.TESTS);
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        gitlabRequestMockProvider.mockCreateProjectForExercise(exercise);
        gitlabRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        jenkinsRequestMockProvider.mockCreateProjectForExercise(exercise);
        jenkinsRequestMockProvider.mockCreateBuildPlan(projectKey, TEMPLATE.getName());
        jenkinsRequestMockProvider.mockCreateBuildPlan(projectKey, SOLUTION.getName());
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, TEMPLATE.getName());
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, SOLUTION.getName());

        doNothing().when(gitService).pushSourceToTargetRepo(any(), any());
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans) throws Exception {
        final var targetProjectKey = exerciseToBeImported.getProjectKey();
        final var targetTemplateRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TEMPLATE);
        final var targetSolutionRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.SOLUTION);
        final var targetTestsRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TESTS);

        gitlabRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false);
        jenkinsRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false);

        // Mock ProgramingExerciseImportService::importRepositories
        gitlabRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetTemplateRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetSolutionRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetTestsRepoName);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();

        doNothing().when(gitService).pushSourceToTargetRepo(any(), any());

        if (!recreateBuildPlans) {
            String templateBuildPlanId = targetProjectKey + "-" + TEMPLATE.getName();
            String solutionBuildPlanId = targetProjectKey + "-" + SOLUTION.getName();

            // Mocks ProgramingExerciseImportService::cloneAndEnableAllBuildPlans
            jenkinsRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported);
            jenkinsRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), targetProjectKey);
            jenkinsRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), targetProjectKey);
            jenkinsRequestMockProvider.mockGivePlanPermissions(targetProjectKey, templateBuildPlanId);
            jenkinsRequestMockProvider.mockGivePlanPermissions(targetProjectKey, solutionBuildPlanId);
            jenkinsRequestMockProvider.mockEnablePlan(targetProjectKey, templateBuildPlanId);
            jenkinsRequestMockProvider.mockEnablePlan(targetProjectKey, solutionBuildPlanId);

            // Mocks ProgramingExerciseImportService::updatePlanRepositoriesInBuildPlans
            mockUpdatePlanRepository(exerciseToBeImported, templateBuildPlanId, ASSIGNMENT_REPO_NAME, targetTemplateRepoName, List.of(ASSIGNMENT_REPO_NAME));
            mockUpdatePlanRepository(exerciseToBeImported, templateBuildPlanId, TEST_REPO_NAME, targetTestsRepoName, List.of());
            mockUpdatePlanRepository(exerciseToBeImported, solutionBuildPlanId, ASSIGNMENT_REPO_NAME, targetSolutionRepoName, List.of());
            mockUpdatePlanRepository(exerciseToBeImported, solutionBuildPlanId, TEST_REPO_NAME, targetTestsRepoName, List.of());
        }
        else {
            // Mocks for recreating the build plans
            jenkinsRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported);
            jenkinsRequestMockProvider.mockCreateBuildPlan(targetProjectKey, TEMPLATE.getName());
            jenkinsRequestMockProvider.mockCreateBuildPlan(targetProjectKey, SOLUTION.getName());
            jenkinsRequestMockProvider.mockTriggerBuild(targetProjectKey, TEMPLATE.getName());
            jenkinsRequestMockProvider.mockTriggerBuild(targetProjectKey, SOLUTION.getName());
        }
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws GitLabApiException {
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists, HttpStatus status)
            throws Exception {
        // Step 1a)
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        // Step 1b)
        gitlabRequestMockProvider.mockConfigureRepository(exercise, username, users, ltiUserExists);
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
        // Step 2a)
        jenkinsRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        // Step 2b)
        jenkinsRequestMockProvider.mockConfigureBuildPlan(exercise, username);
        // Note: Step 2c) is not needed in the Jenkins setup
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var repoName = projectKey.toLowerCase() + "-" + username;
        mockUpdatePlanRepository(exercise, username, ASSIGNMENT_REPO_NAME, repoName, List.of());
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy)
            throws IOException, URISyntaxException {
        jenkinsRequestMockProvider.mockUpdatePlanRepository(exercise.getProjectKey(), planName);
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws Exception {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        gitlabRequestMockProvider.mockRemoveMemberFromRepository(repositorySlug, firstStudent);
    }

    @Override
    public void mockRepositoryWritePermissions(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws Exception {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        gitlabRequestMockProvider.mockAddMemberToRepository(repositorySlug, newStudent);
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
        jenkinsRequestMockProvider.mockGetBuildStatus(projectKey, buildPlanId, true, false, false);

        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, buildPlanId);
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws Exception {
        final String slug = "test201904bprogrammingexercise6-exercise-testuser";
        final String hash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        final String projectKey = participation.getProgrammingExercise().getProjectKey();
        mockFetchCommitInfo(projectKey, slug, hash);
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, participation.getBuildPlanId());
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        jenkinsRequestMockProvider.mockTriggerBuild(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId());
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        mockCopyBuildPlan(participation);
        mockConfigureBuildPlan(participation);
        jenkinsRequestMockProvider.mockTriggerBuild(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId());
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, Set<String> oldGroups) throws Exception {
        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), oldGroups, true);
        gitlabRequestMockProvider.mockUpdateVcsUser(oldLogin, user, oldGroups, user.getGroups(), true);
    }

    @Override
    public void mockCreateUserInUserManagement(User user) throws Exception {
        gitlabRequestMockProvider.mockCreateVcsUser(user);
        jenkinsRequestMockProvider.mockCreateUser(user);
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement) throws Exception {
        gitlabRequestMockProvider.mockDeleteVcsUser(user.getLogin());
        jenkinsRequestMockProvider.mockDeleteUser(user, userExistsInUserManagement);
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldTeachingAssistantGroup) throws Exception {
        gitlabRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldTeachingAssistantGroup);
        jenkinsRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldTeachingAssistantGroup);
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
    public void mockDeleteRepository(String projectKey, String repostoryName) throws Exception {
        gitlabRequestMockProvider.mockDeleteRepository(projectKey);
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey) throws Exception {
        gitlabRequestMockProvider.mockDeleteProject(projectKey);
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName) throws Exception {
        jenkinsRequestMockProvider.mockDeleteBuildPlan(projectKey, planName);
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey) throws Exception {
        jenkinsRequestMockProvider.mockDeleteBuildPlanProject(projectKey);
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group) throws Exception {
        gitlabRequestMockProvider.mockUpdateVcsUser(user.getLogin(), user, Set.of(), Set.of(group), false);
        jenkinsRequestMockProvider.mockAddUsersToGroups(user.getLogin(), Set.of(group));
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group) throws Exception {
        gitlabRequestMockProvider.mockUpdateVcsUser(user.getLogin(), user, Set.of(group), Set.of(), false);
        jenkinsRequestMockProvider.mockRemoveUserFromGroups(Set.of(group));
        jenkinsRequestMockProvider.mockAddUsersToGroups(user.getLogin(), Set.of(group));
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding) throws Exception {
        jenkinsRequestMockProvider.mockGetBuildStatus(projectKey, planName, planExistsInCi, planIsActive, planIsBuilding);
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws Exception {
        jenkinsRequestMockProvider.mockHealth(isRunning, httpStatus);
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) throws Exception {
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, existsInVcs);
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi) throws Exception {
        jenkinsRequestMockProvider.mockCheckIfProjectExists(exercise, existsInCi);
    }

    @Override
    public void mockRepositoryUrlIsValid(VcsRepositoryUrl repositoryUrl, String projectKey, boolean isUrlValid) throws Exception {
        gitlabRequestMockProvider.mockRepositoryUrlIsValid(repositoryUrl, isUrlValid);
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String buildPlanId, boolean buildPlanExists) throws Exception {
        jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(projectKey, buildPlanId, buildPlanExists);
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws Exception {
        var projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
        var buildPlanId = programmingExerciseParticipation.getBuildPlanId();
        jenkinsRequestMockProvider.mockTriggerBuild(projectKey, buildPlanId);
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws Exception {
        gitlabRequestMockProvider.setRepositoryPermissionsToReadOnly(repositoryUrl, projectKey, users);
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean ltiUserExists) throws Exception {
        gitlabRequestMockProvider.mockConfigureRepository(exercise, participantIdentifier, students, ltiUserExists);
    }

    @Override
    public void resetMockProvider() {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
    }
}
