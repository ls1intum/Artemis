package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AEOLUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LDAP;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_THEIA;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyJolService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exam.service.ExamLiveEventsService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisEventService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.icl.DockerClientTestService;
import de.tum.cit.aet.artemis.programming.icl.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.programming.icl.TestBuildAgentConfiguration;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCITriggerService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCService;
import de.tum.cit.aet.artemis.programming.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

// Must start up an actual web server such that the tests can communicate with the ArtemisGitServlet using JGit.
// Otherwise, only MockMvc requests could be used. The port this runs on is defined at server.port (see @TestPropertySource).
// Note: Cannot use WebEnvironment.RANDOM_PORT here because artemis.version-control.url must be set to the correct port in the @TestPropertySource annotation.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ResourceLock("AbstractSpringIntegrationLocalCILocalVCTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
// NOTE: in a "single node" environment, PROFILE_BUILDAGENT must be before PROFILE_CORE to avoid issues
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_ARTEMIS, PROFILE_BUILDAGENT, PROFILE_CORE, PROFILE_SCHEDULING, PROFILE_LOCALCI, PROFILE_LOCALVC, PROFILE_LDAP, PROFILE_LTI,
        PROFILE_AEOLUS, PROFILE_THEIA, PROFILE_IRIS, PROFILE_ATHENA, "local" })
// Note: the server.port property must correspond to the port used in the artemis.version-control.url property.
@TestPropertySource(properties = { "artemis.user-management.use-external=false", "artemis.sharing.enabled=true", "artemis.continuous-integration.specify-concurrent-builds=true",
        "artemis.continuous-integration.concurrent-build-size=1", "artemis.continuous-integration.asynchronous=false",
        "artemis.continuous-integration.build.images.java.default=dummy-docker-image", "artemis.continuous-integration.image-cleanup.enabled=true",
        "artemis.continuous-integration.image-cleanup.disk-space-threshold-mb=1000000000", "spring.liquibase.enabled=true", "artemis.iris.health-ttl=500",
        "info.contact=test@localhost", "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_localci_localvc", "artemis.version-control.build-agent-use-ssh=true",
        "artemis.version-control.ssh-private-key-folder-path=local/server-integration-test/ssh-keys", "artemis.hyperion.enabled=true", "artemis.nebula.enabled=false" })
@ContextConfiguration(classes = TestBuildAgentConfiguration.class)
public abstract class AbstractSpringIntegrationLocalCILocalVCTest extends AbstractArtemisIntegrationTest {

    private static final int serverPort;

    private static final int sshPort;

    private static final int hazelcastPort;

    // Static initializer runs before @DynamicPropertySource, ensuring ports are available when Spring context starts
    static {
        serverPort = findAvailableTcpPort();
        sshPort = findAvailableTcpPort();
        hazelcastPort = findAvailableTcpPort();
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> serverPort);
        registry.add("artemis.version-control.url", () -> "http://localhost:" + serverPort);
        registry.add("artemis.version-control.ssh-port", () -> sshPort);
        registry.add("artemis.version-control.ssh-template-clone-url", () -> "ssh://git@localhost:" + sshPort + "/");
        registry.add("spring.hazelcast.port", () -> hazelcastPort);
    }

    private static int findAvailableTcpPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not find an available TCP port", e);
        }
    }

    // Spy is only used for simulating non-feasible failure scenarios. Please use the real bean otherwise.
    @MockitoSpyBean
    protected GitService gitServiceSpy;

    @Autowired
    protected LocalVCLocalCITestService localVCLocalCITestService;

    @Autowired
    protected DockerClientTestService dockerClientTestService;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    protected ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository;

    @Autowired
    protected TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    protected SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    protected ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    protected BuildJobTestRepository buildJobRepository;

    @MockitoSpyBean
    protected LdapUserService ldapUserService;

    @MockitoSpyBean
    protected SpringSecurityLdapTemplate ldapTemplate;

    @Autowired
    protected LocalVCService versionControlService;

    @MockitoSpyBean
    protected LocalCIService continuousIntegrationService;

    @MockitoSpyBean
    protected LocalCITriggerService localCITriggerService;

    @MockitoSpyBean
    protected BuildAgentConfiguration buildAgentConfiguration;

    @MockitoSpyBean
    protected ResourceLoaderService resourceLoaderService;

    @MockitoSpyBean
    protected ProgrammingMessagingService programmingMessagingService;

    @MockitoSpyBean
    protected ExamLiveEventsService examLiveEventsService;

    @MockitoSpyBean
    protected IrisCourseChatSessionService irisCourseChatSessionService;

    @MockitoSpyBean
    protected CompetencyJolService competencyJolService;

    @MockitoSpyBean
    protected PyrisPipelineService pyrisPipelineService;

    @MockitoSpyBean
    protected IrisExerciseChatSessionService irisExerciseChatSessionService;

    @MockitoSpyBean
    protected PyrisEventService pyrisEventService;

    @MockitoSpyBean
    protected CompetencyProgressService competencyProgressService;

    @MockitoSpyBean
    protected CompetencyProgressApi competencyProgressApi;

    // we explicitly want a mock here, as we don't want to test the actual chat model calls and avoid any autoconfiguration or instantiation of Spring AI internals
    @MockitoBean
    protected ChatModel azureOpenAiChatModel;

    protected URI localVCBaseUri;

    @Value("${artemis.version-control.url}")
    public void setLocalVCBaseUri(URI localVCBaseUri) {
        this.localVCBaseUri = localVCBaseUri;
        ProgrammingExerciseFactory.localVCBaseUri = localVCBaseUri; // Set the static field in ProgrammingExerciseFactory for convenience
    }

    @Value("${artemis.version-control.local-vcs-repo-path}")
    protected Path localVCBasePath;

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

    protected static DockerClient dockerClientMock;

    @BeforeEach
    void clearBuildJobsBefore() {
        buildJobRepository.deleteAll();
    }

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        Mockito.reset(gitServiceSpy, continuousIntegrationService, localCITriggerService, resourceLoaderService, programmingMessagingService, competencyProgressService,
                competencyProgressApi);
        super.resetSpyBeans();
    }

    @AfterEach
    void clearBuildJobsAfter() {
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
    public void mockGetBuildPlanConfig(String projectKey, String planName) {
        // not needed for localVCS/CI
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) {
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
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockGetCiProjectMissing(ProgrammingExercise exercise) {
        // not relevant for local VC and local CI
    }
}
