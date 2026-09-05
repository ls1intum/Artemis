package de.tum.cit.aet.artemis.shared.base;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.weaviate.WeaviateContainer;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.exam.service.ExamLiveEventsService;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisEventService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.localci.service.DockerClientTestService;
import de.tum.cit.aet.artemis.localci.service.LocalCIService;
import de.tum.cit.aet.artemis.localci.service.LocalCITriggerService;
import de.tum.cit.aet.artemis.localci.service.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.localci.service.TestBuildAgentConfiguration;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCService;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.shared.WeaviateTestContainerFactory;

/**
 * Everything the local CI / local VC test contexts have in common: the beans the tests reach for, the spies they reset,
 * and the mock connector methods this topology does not need.
 * <p>
 * Two contexts extend it, so that the classes of this topology run in two lanes rather than one. A bucket holds an
 * exclusive lock and therefore runs one class at a time however idle the machine is, so a second bucket is the only way
 * to give the topology a second lane. What has to differ per context - the ports it binds, the paths it writes to, its
 * Hazelcast instance and its Weaviate collection - stays in the subclasses.
 *
 * @see AbstractSpringIntegrationLocalCILocalVCTest
 * @see AbstractSpringIntegrationLocalCILocalVCBatchTest
 */
public abstract class AbstractSpringIntegrationLocalCILocalVCTestBase extends AbstractArtemisIntegrationTest {

    // Shared by both contexts: one container, but each context gets its own collection prefix (see the subclasses),
    // because their databases have overlapping entity ids and a shared collection would let them see each other's rows.
    protected static final WeaviateContainer weaviateContainer = WeaviateTestContainerFactory.getContainer();

    private static final Logger log = LoggerFactory.getLogger(AbstractSpringIntegrationLocalCILocalVCTestBase.class);

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
    protected IrisChatSessionService irisChatSessionService;

    @MockitoSpyBean
    protected IrisCitationService irisCitationService;

    @MockitoSpyBean
    protected PyrisPipelineService pyrisPipelineService;

    @MockitoSpyBean
    protected PyrisEventService pyrisEventService;

    @MockitoSpyBean
    protected CompetencyProgressService competencyProgressService;

    @MockitoSpyBean
    protected CompetencyProgressApi competencyProgressApi;

    @MockitoSpyBean
    protected PyrisFaqApi pyrisFaqApi;

    // we explicitly want a mock here, as we don't want to test the actual chat model calls and avoid any autoconfiguration or instantiation of Spring AI internals
    @MockitoBean
    protected ChatModel azureOpenAiChatModel;

    protected URI localVCBaseUri;

    @Value("${artemis.version-control.url}")
    public void setLocalVCBaseUri(URI localVCBaseUri) {
        this.localVCBaseUri = localVCBaseUri;
        // Hand the factory this context's LocalVC URL, so exercises it builds for this test address this context's server.
        ProgrammingExerciseFactory.setLocalVCBaseUri(localVCBaseUri);
    }

    @Value("${artemis.version-control.local-vcs-repo-path}")
    protected Path localVCBasePath;

    protected static final String DUMMY_COMMIT_HASH = "1234567890abcdef";

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

    @BeforeEach
    void stubChatModelDefaultOptions() {
        // Since Spring AI 2.0 the ChatClient merges request options into the model's options (getOptions since RC1, getDefaultOptions before), which must be non-null
        Mockito.when(azureOpenAiChatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        Mockito.when(azureOpenAiChatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    /**
     * Puts the two LDAP spies into a known state before every test.
     * <p>
     * They spy on beans that talk to a directory server which does not exist here, so what they do is decided
     * entirely by stubbing - and resetting them after each test, as the suite must, would otherwise leave the next
     * test calling the real thing. Rejecting by default is the safe baseline: a test that needs LDAP to accept says
     * so itself, and its own {@code @BeforeEach} runs after this one, so the more specific stub wins.
     * <p>
     * Without this, whatever the previous test stubbed decided the outcome. {@code LocalVCLocalCIIntegrationTest}
     * sorts before every {@code localvc} class and stubs {@code compare} to accept any password, which is how tests
     * asserting that a deactivated account cannot authenticate saw it authenticate anyway.
     */
    @BeforeEach
    void rejectLdapAuthenticationByDefault() {
        Mockito.doReturn(Optional.empty()).when(ldapUserService).findByLogin(ArgumentMatchers.anyString());
        Mockito.doReturn(false).when(ldapTemplate).compare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
    }

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        Mockito.reset(gitServiceSpy, continuousIntegrationService, localCITriggerService, buildAgentConfiguration, resourceLoaderService, programmingMessagingService,
                competencyProgressService, competencyProgressApi, irisCitationService, irisChatSessionService, pyrisPipelineService, pyrisEventService, ldapUserService,
                ldapTemplate, examLiveEventsService, pyrisFaqApi, azureOpenAiChatModel);
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
