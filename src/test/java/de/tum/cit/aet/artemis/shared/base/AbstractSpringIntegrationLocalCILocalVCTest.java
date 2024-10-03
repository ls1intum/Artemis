package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AEOLUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exam.service.ExamLiveEventsService;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.localvcci.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.programming.localvcci.util.TestBuildAgentConfiguration;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCService;
import de.tum.cit.aet.artemis.programming.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

// Must start up an actual web server such that the tests can communicate with the ArtemisGitServlet using JGit.
// Otherwise, only MockMvc requests could be used. The port this runs on is defined at server.port (see @TestPropertySource).
// Note: Cannot use WebEnvironment.RANDOM_PORT here because artemis.version-control.url must be set to the correct port in the @TestPropertySource annotation.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ResourceLock("AbstractSpringIntegrationLocalCILocalVCTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
// NOTE: in a "single node" environment, PROFILE_BUILDAGENT must be before PROFILE_CORE to avoid issues
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_ARTEMIS, PROFILE_BUILDAGENT, PROFILE_CORE, PROFILE_SCHEDULING, PROFILE_LOCALCI, PROFILE_LOCALVC, "ldap-only", PROFILE_LTI,
        PROFILE_AEOLUS, PROFILE_IRIS })

// Note: the server.port property must correspond to the port used in the artemis.version-control.url property.
@TestPropertySource(properties = { "server.port=49152", "artemis.version-control.url=http://localhost:49152", "artemis.user-management.use-external=false",
        "artemis.version-control.local-vcs-repo-path=${java.io.tmpdir}", "artemis.build-logs-path=${java.io.tmpdir}/build-logs",
        "artemis.continuous-integration.specify-concurrent-builds=true", "artemis.continuous-integration.concurrent-build-size=1",
        "artemis.continuous-integration.asynchronous=false", "artemis.continuous-integration.build.images.java.default=dummy-docker-image",
        "artemis.continuous-integration.image-cleanup.enabled=true", "artemis.continuous-integration.image-cleanup.disk-space-threshold-mb=1000000000",
        "spring.liquibase.enabled=true", "artemis.iris.health-ttl=500", "artemis.version-control.ssh-private-key-folder-path=${java.io.tmpdir}",
        "artemis.version-control.build-agent-use-ssh=true", "info.contact=test@localhost", "artemis.version-control.ssh-template-clone-url=ssh://git@localhost:7921/",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_localcilocalvc" })
@ContextConfiguration(classes = TestBuildAgentConfiguration.class)
public abstract class AbstractSpringIntegrationLocalCILocalVCTest extends AbstractArtemisIntegrationTest {

    @Autowired
    protected LocalVCLocalCITestService localVCLocalCITestService;

    @SpyBean
    protected LdapUserService ldapUserService;

    @SpyBean
    protected SpringSecurityLdapTemplate ldapTemplate;

    @SpyBean
    protected LocalVCService versionControlService;

    @SpyBean
    protected LocalCIService continuousIntegrationService;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    protected TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    protected SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    protected ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    protected BuildJobTestRepository buildJobRepository;

    /**
     * This is the mock(DockerClient.class) provided by the {@link TestBuildAgentConfiguration}.
     * Subclasses can use this to dynamically mock methods of the DockerClient.
     */
    @Autowired
    protected DockerClient dockerClient;

    @SpyBean
    protected ResourceLoaderService resourceLoaderService;

    @SpyBean
    protected ProgrammingMessagingService programmingMessagingService;

    @SpyBean
    protected ExamLiveEventsService examLiveEventsService;

    @SpyBean
    protected GroupNotificationScheduleService groupNotificationScheduleService;

    @Value("${artemis.version-control.url}")
    protected URL localVCBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    protected String localVCBasePath;

    protected static final String DUMMY_COMMIT_HASH = "1234567890abcdef";

    protected static final String DUMMY_COMMIT_HASH_VALID = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";

    private static final Path TEST_RESULTS_PATH = Path.of("src", "test", "resources", "test-data", "test-results");

    private static final Path GRADLE_TEST_RESULTS_PATH = TEST_RESULTS_PATH.resolve("java-gradle");

    protected static final Path ALL_FAIL_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("all-fail");

    protected static final Path PARTLY_SUCCESSFUL_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("partly-successful");

    protected static final Path ALL_SUCCEED_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("all-succeed");

    protected static final Path FAULTY_FILES_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("faulty-files");

    protected static final Path OLD_REPORT_FORMAT_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("old-report-format");

    protected static final Path EMPTY_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("empty");

    private static final Path SCA_REPORTS_PATH = Path.of("src", "test", "resources", "test-data", "static-code-analysis", "reports");

    protected static final Path SPOTBUGS_RESULTS_PATH = SCA_REPORTS_PATH.resolve("spotbugsXml.xml");

    protected static final Path CHECKSTYLE_RESULTS_PATH = SCA_REPORTS_PATH.resolve("checkstyle-result.xml");

    protected static final Path PMD_RESULTS_PATH = SCA_REPORTS_PATH.resolve("pmd.xml");

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        Mockito.reset(versionControlService, continuousIntegrationService, resourceLoaderService, programmingMessagingService);
        super.resetSpyBeans();
    }

    @AfterEach
    void clearBuildJobs() {
        buildJobRepository.deleteAll();
    }

    /**
     * Note: Mocking requests to the VC and CI server is not necessary for local VC and local CI.
     * The VC system is part of the application context and can thus be called directly.
     * For the CI system, all communication with the DockerClient is mocked (see {@link TestBuildAgentConfiguration}).
     */

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject, boolean useCustomBuildPlanDefinition, boolean useCustomBuildPlanWorked) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) {
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
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs) {
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
    public void mockRepositoryUriIsValid(VcsRepositoryUri vcsTemplateRepositoryUri, String projectKey1, boolean b) throws Exception {
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
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUri repositoryUri, String projectKey1, Set<User> users) throws Exception {
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
