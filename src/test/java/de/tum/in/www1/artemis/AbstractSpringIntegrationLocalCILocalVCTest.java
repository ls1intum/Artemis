package de.tum.in.www1.artemis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Container;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.localvcci.LocalVCLocalCITestService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

// Must start up an actual web server such that the tests can communicate with the ArtemisGitServlet using JGit.
// Otherwise, only MockMvc requests could be used. The port this runs on is defined at server.port (see @TestPropertySource).
// Note: Cannot use WebEnvironment.RANDOM_PORT here because artemis.version-control.url must be set to the correct port in the @TestPropertySource annotation.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@AutoConfigureEmbeddedDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "localci", "localvc", "scheduling" })
// Note: the server.port property must correspond to the port used in the artemis.version-control.url property.
@TestPropertySource(properties = { "server.port=49152", "artemis.version-control.url=http://localhost:49152", "artemis.version-control.local-vcs-repo-path=${java.io.tmpdir}",
        "artemis.continuous-integration.thread-pool-size=1", "artemis.continuous-integration.asynchronous=false",
        "artemis.continuous-integration.build.images.java.default=dummy-docker-image", "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationLocalCILocalVCTest extends AbstractArtemisIntegrationTest {

    @Autowired
    protected LocalVCLocalCITestService localVCLocalCITestService;

    @SpyBean
    protected LocalVCService versionControlService;

    @SpyBean
    protected LocalCIService continuousIntegrationService;

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    protected SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    protected ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @SpyBean
    protected DockerClient dockerClient;

    @SpyBean
    protected ResourceLoaderService resourceLoaderService;

    @SpyBean
    protected ProgrammingMessagingService programmingMessagingService;

    @Value("${artemis.version-control.url}")
    protected URL localVCBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    protected String localVCBasePath;

    protected static final String DUMMY_COMMIT_HASH = "1234567890abcdef";

    protected static final Path ALL_FAIL_TEST_RESULTS_PATH = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "all-fail");

    protected static final Path PARTLY_SUCCESSFUL_TEST_RESULTS_PATH = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "partly-successful");

    protected static final Path ALL_SUCCEED_TEST_RESULTS_PATH = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "all-succeed");

    protected static final Path FAULTY_FILES_TEST_RESULTS_PATH = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "faulty-files");

    @AfterEach
    protected void resetSpyBeans() {
        Mockito.reset(versionControlService, continuousIntegrationService, dockerClient, resourceLoaderService, programmingMessagingService);
        super.resetSpyBeans();
    }

    protected void mockDockerClientMethods() throws IOException {
        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        InspectImageResponse inspectImageResponse = new InspectImageResponse();
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        doReturn(inspectImageResponse).when(inspectImageCmd).exec();

        String dummyContainerId = "1234567890";

        // Mock dockerClient.createContainerCmd(String dockerImage).withHostConfig(HostConfig hostConfig).withEnv(String... env).withCmd(String... cmd).exec()
        CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse createContainerResponse = new CreateContainerResponse();
        createContainerResponse.setId(dummyContainerId);
        doReturn(createContainerCmd).when(dockerClient).createContainerCmd(anyString());
        doReturn(createContainerCmd).when(createContainerCmd).withHostConfig(any());
        doReturn(createContainerCmd).when(createContainerCmd).withEnv(anyString(), anyString());
        doReturn(createContainerCmd).when(createContainerCmd).withCmd(anyString(), anyString(), anyString());
        doReturn(createContainerResponse).when(createContainerCmd).exec();

        // Mock dockerClient.startContainerCmd(String containerId)
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        doReturn(startContainerCmd).when(dockerClient).startContainerCmd(anyString());

        // Mock dockerClient.execCreateCmd(String containerId).withAttachStdout(Boolean attachStdout).withAttachStderr(Boolean attachStderr).withCmd(String... cmd).exec()
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateCmdResponse = mock(ExecCreateCmdResponse.class);
        doReturn(execCreateCmd).when(dockerClient).execCreateCmd(anyString());
        doReturn(execCreateCmd).when(execCreateCmd).withAttachStdout(anyBoolean());
        doReturn(execCreateCmd).when(execCreateCmd).withAttachStderr(anyBoolean());
        doReturn(execCreateCmd).when(execCreateCmd).withCmd(anyString(), anyString());
        doReturn(execCreateCmdResponse).when(execCreateCmd).exec();
        doReturn("1234").when(execCreateCmdResponse).getId();

        // Mock dockerClient.execStartCmd(String execId).exec(T resultCallback)
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        doReturn(execStartCmd).when(dockerClient).execStartCmd(anyString());
        doAnswer(invocation -> {
            // Stub the 'exec' method of the 'ExecStartCmd' to call the 'onComplete' method of the provided 'ResultCallback.Adapter', which simulates the command completing
            // immediately.
            ResultCallback.Adapter<?> callback = invocation.getArgument(0);
            callback.onComplete();
            return null;
        }).when(execStartCmd).exec(any());

        // Mock dockerClient.listContainersCmd().withShowAll(true).exec().
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        doReturn(listContainersCmd).when(listContainersCmd).withShowAll(anyBoolean());
        Container container = mock(Container.class);
        doReturn(List.of(container)).when(listContainersCmd).exec();
        doReturn(dummyContainerId).when(container).getId();
        doReturn("running").when(container).getState();

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the assignment and the tests repository.
        // Note: The stub needs to receive the same object twice in case there are two requests to the same method. Usually, specifying one doReturn() is enough to make the stub
        // return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/assignment-repository/.git/refs/heads/[^/]+",
                Map.of("assignmentComitHash", DUMMY_COMMIT_HASH), Map.of("assignmentComitHash", DUMMY_COMMIT_HASH));
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));
        String mockData = "mockData";
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/build/test-results/test", Map.of(mockData, mockData),
                Map.of(mockData, mockData));
    }

    /**
     * Note: Mocking requests to the VC and CI server is not necessary for local VC and local CI.
     * The VC system is part of the application context and can thus be called directly.
     * For the CI system, all communication with the DockerClient is mocked (see {@link #mockDockerClientMethods()}).
     */

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
            boolean shouldPlanEnableFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFetchCommitInfo(String projectKey1, String repositorySlug, String hash) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void resetMockProvider() {
        // Not implemented for local VC and local CI
    }

    @Override
    public void verifyMocks() {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteRepository(String projectKey1, String repositoryName, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey1, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey1, String planName, boolean shouldFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey1, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockGetBuildPlan(String projectKey1, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey1, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryUrlIsValid(VcsRepositoryUrl vcsTemplateRepositoryUrl, String projectKey1, boolean b) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey1, Set<User> users) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) {
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
