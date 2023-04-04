package de.tum.in.www1.artemis;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.localvcci.LocalVCLocalCITestConfig;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

// Must start up an actual web server such that the tests can communicate with the ArtemisGitServlet using JGit.
// Otherwise, only MockMvc requests could be used. The port this runs on is defined at server.port in application.myl.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@AutoConfigureEmbeddedDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "localci", "localvc", "scheduling" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false", "artemis.version-control.local-vcs-repo-path=${java.io.tmpdir}",
        "artemis.version-control.url=http://localhost:8080", "artemis.continuous-integration.build.images.java.default=dummy-docker-image" })
// Contains the mock setup for the DockerClient.
@ContextConfiguration(classes = LocalVCLocalCITestConfig.class)
public abstract class AbstractSpringIntegrationLocalCILocalVCTest extends AbstractArtemisIntegrationTest {

    @Value("${artemis.version-control.url}")
    protected String localVCSBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    protected Path localVCSBasePath;

    // @Autowired
    // protected LocalVCLocalCITestConfig localVCLocalCITestConfig;

    @LocalServerPort
    protected int port;

    @AfterEach
    protected void resetSpyBeans() {
        super.resetSpyBeans();
    }

    // Note: Mocking requests to the VC and CI server is not necessary for local VC and local CI.
    // The VC system is part of the application context and can thus be called directly.
    // For the CI system, all communication with the DockerClient is mocked.

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception {

    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy)
            throws IOException, URISyntaxException {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException, GitLabApiException {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) throws MalformedURLException, URISyntaxException, JsonProcessingException {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) throws URISyntaxException, JsonProcessingException {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) throws URISyntaxException {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void resetMockProvider() throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void verifyMocks() {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteRepository(String projectKey, String repositoryName, boolean shouldFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey, boolean shouldFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild)
            throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryUrlIsValid(VcsRepositoryUrl vcsTemplateRepositoryUrl, String projectKey, boolean b) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) throws IOException, GitLabApiException {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUserExists(String username) throws Exception {
        // Not implemented for local VC and local CI
    }
}
