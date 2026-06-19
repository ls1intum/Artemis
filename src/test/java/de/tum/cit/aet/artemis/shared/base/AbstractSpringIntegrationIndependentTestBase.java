package de.tum.cit.aet.artemis.shared.base;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.PasskeyAuthenticationService;
import de.tum.cit.aet.artemis.admin.service.SbomService;
import de.tum.cit.aet.artemis.admin.service.VulnerabilityService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.service.ArtemisVersionService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.exam.service.ExamLiveEventsService;
import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSService;
import de.tum.cit.aet.artemis.lti.test_repository.LtiPlatformConfigurationTestRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.videosource.service.GocastApprovalLinkService;
import de.tum.cit.aet.artemis.videosource.service.GocastConnectorService;
import de.tum.cit.aet.artemis.videosource.service.TumLiveService;

/**
 * Shared implementation for independent integration test buckets.
 * <p>
 * Do not tag this class directly. JUnit tags are inherited, so concrete bucket classes must extend one of the tagged sibling classes.
 */
public abstract class AbstractSpringIntegrationIndependentTestBase extends AbstractArtemisIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractSpringIntegrationIndependentTestBase.class);

    @MockitoSpyBean
    protected OAuth2JWKSService oAuth2JWKSService;

    @MockitoSpyBean
    protected LtiPlatformConfigurationTestRepository ltiPlatformConfigurationRepository;

    @MockitoSpyBean
    protected ExamLiveEventsService examLiveEventsService;

    @MockitoSpyBean
    protected GroupNotificationScheduleService groupNotificationScheduleService;

    @MockitoSpyBean
    protected CompetencyProgressService competencyProgressService;

    @MockitoSpyBean
    protected CompetencyProgressApi competencyProgressApi;

    // NOTE: MockitoBean is used here because ChatModel and ChatClient cannot be instantiated in tests without Azure OpenAI credentials
    // These beans are provided by SpringAIConfiguration in production, but need to be mocked for tests
    @MockitoBean
    protected ChatModel chatModel;

    @MockitoBean
    protected ChatClient chatClient;

    @MockitoBean
    protected ChatMemory chatMemory;

    @MockitoBean
    protected ChatMemoryRepository chatMemoryRepository;

    // Mock for TUM Live service used in TUM Live playlist resource
    @MockitoBean
    protected TumLiveService tumLiveService;

    // Mocks for the gocast integration — the connector and approval-link builder require
    // external configuration (gocast base URL and service-account token). They are replaced
    // with Mockito beans so no real HTTP calls are made in tests.
    @MockitoBean
    protected GocastConnectorService gocastConnectorService;

    @MockitoBean
    protected GocastApprovalLinkService gocastApprovalLinkService;

    // Mock PasskeyAuthenticationService to allow super admin operations in tests
    // The @EnforceSuperAdmin annotation requires passkey authentication to be mocked
    @MockitoSpyBean
    protected PasskeyAuthenticationService passkeyAuthenticationService;

    // Spy beans moved here to avoid creating separate Spring contexts in AdminSbomResourceIntegrationTest and VulnerabilityScanScheduleServiceTest
    @MockitoSpyBean
    protected ArtemisVersionService artemisVersionService;

    @MockitoSpyBean
    protected VulnerabilityService vulnerabilityService;

    @MockitoSpyBean
    protected ProfileService profileService;

    @MockitoSpyBean
    protected SbomService sbomService;

    @BeforeEach
    protected void setupSpringAIMocks() {
        if (chatModel != null) {
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mocked AI response for testing")))));
            // Since Spring AI 2.0 the ChatClient merges request options into the model's options (getOptions since RC1, getDefaultOptions before), which must be non-null
            when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
            when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        }
        // Mock passkey authentication to always return true for super admin operations in tests
        // Use doReturn instead of when().thenReturn() because the method throws an exception
        doReturn(true).when(passkeyAuthenticationService).isAuthenticatedWithSuperAdminApprovedPasskey();
        // Default to SBOM-available in tests; individual tests can override to exercise the missing-SBOM path.
        // VulnerabilityScanScheduleService short-circuits with no email when isSbomAvailable() returns false.
        doReturn(true).when(sbomService).isSbomAvailable();
    }

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        Mockito.reset(oAuth2JWKSService, ltiPlatformConfigurationRepository, competencyProgressService, competencyProgressApi);
        Mockito.reset(artemisVersionService, vulnerabilityService, profileService, sbomService);
        if (chatModel != null) {
            Mockito.reset(chatModel);
        }
        if (chatClient != null) {
            Mockito.reset(chatClient);
        }
        if (tumLiveService != null) {
            Mockito.reset(tumLiveService);
        }
        if (gocastConnectorService != null) {
            Mockito.reset(gocastConnectorService);
        }
        if (gocastApprovalLinkService != null) {
            Mockito.reset(gocastApprovalLinkService);
        }
        if (chatMemoryRepository != null) {
            Mockito.reset(chatMemoryRepository);
        }
        if (chatMemory != null) {
            Mockito.reset(chatMemory);
        }
        super.resetSpyBeans();
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject, boolean useCustomBuildPlanDefinition, boolean useCustomBuildPlanWorked) {
        log.debug("Called mockConnectorRequestsForSetup with args {}, {}, {}, {}", exercise, failToCreateCiProject, useCustomBuildPlanDefinition, useCustomBuildPlanWorked);
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos) {
        log.debug("Called mockConnectorRequestsForImport with args {}, {}, {}, {}", sourceExercise, exerciseToBeImported, recreateBuildPlans, addAuxRepos);
    }

    @Override
    public void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) {
        log.debug("Called mockConnectorRequestForImportFromFile with args {}", exerciseForImport);
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) {
        log.debug("Called mockImportProgrammingExerciseWithFailingEnablePlan with args {}, {}, {}, {}", sourceExercise, exerciseToBeImported, planExistsInCi, shouldPlanEnableFail);
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        log.debug("Called mockConnectorRequestsForStartParticipation with args {}, {}, {}, {}", exercise, username, users, ltiUserExists);
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        log.debug("Called mockConnectorRequestsForResumeParticipation with args {}, {}, {}, {}", exercise, username, users, ltiUserExists);
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        log.debug("Called mockUpdatePlanRepositoryForParticipation with args {}, {}", exercise, username);
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs) {
        log.debug("Called mockUpdatePlanRepository with args {}, {}, {}, {}", exercise, planName, repoNameInCI, repoNameInVcs);
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockCopyBuildPlan with args {}", participation);
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockConfigureBuildPlan with args {}", participation);
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockTriggerFailedBuild with args {}", participation);
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockNotifyPush with args {}", participation);
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockTriggerParticipationBuild with args {}", participation);
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockTriggerInstructorBuildAll with args {}", participation);
    }

    @Override
    public void resetMockProvider() {
        log.debug("Called resetMockProvider");
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) {
        log.debug("Called mockDeleteBuildPlan with args {}, {}, {}", projectKey, planName, shouldFail);
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) {
        log.debug("Called mockDeleteBuildPlanProject with args {}, {}", projectKey, shouldFail);
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) {
        log.debug("Called mockGetBuildPlan with args {}, {}, {}, {}, {}, {}", projectKey, planName, planExistsInCi, planIsActive, planIsBuilding, failToGetBuild);
    }

    @Override
    public void mockGetBuildPlanConfig(String projectKey, String planName) {
        log.debug("Called mockGetBuildPlanConfig with args {}, {}", projectKey, planName);
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) {
        log.debug("Called mockHealthInCiService with args {}, {}", isRunning, httpStatus);
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) {
        log.debug("Called mockCheckIfProjectExistsInCi with args {}, {}, {}", exercise, existsInCi, shouldFail);
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) {
        log.debug("Called mockCheckIfBuildPlanExists with args {}, {}, {}, {}", projectKey, templateBuildPlanId, buildPlanExists, shouldFail);
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        log.debug("Called mockTriggerBuild with args {}", solutionParticipation);
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        log.debug("Called mockTriggerBuildFailed with args {}", solutionParticipation);
    }

    @Override
    public void mockGetCiProjectMissing(ProgrammingExercise exercise) {
        log.debug("Requested CI project {}", exercise.getProjectKey());
    }
}
