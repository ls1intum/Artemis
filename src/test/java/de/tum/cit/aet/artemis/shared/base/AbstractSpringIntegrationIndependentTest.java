package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AEOLUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_APOLLON;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_INDEPENDENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.service.ExamLiveEventsService;
import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSService;
import de.tum.cit.aet.artemis.lti.test_repository.LtiPlatformConfigurationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * This SpringBootTest is used for tests that only require a minimal set of Active Spring Profiles.
 */
@ResourceLock("AbstractSpringIntegrationIndependentTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
// TODO: PROFILE_AEOLUS is bound to PROGRAMMING and LOCAL_VC and should not be active in an independent test context.
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_TEST_INDEPENDENT, PROFILE_ARTEMIS, PROFILE_CORE, PROFILE_SCHEDULING, PROFILE_ATHENA, PROFILE_APOLLON, PROFILE_IRIS, PROFILE_AEOLUS,
        PROFILE_LTI, "local" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false", "artemis.sharing.enabled=true", "artemis.user-management.passkey.enabled=true",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_independent" })
public abstract class AbstractSpringIntegrationIndependentTest extends AbstractArtemisIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractSpringIntegrationIndependentTest.class);

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

    // AtlasCompanion mocks
    @MockitoBean
    protected ChatModel chatModel;

    @MockitoBean
    protected ChatClient chatClient;

    @MockitoBean
    protected ChatMemoryRepository chatMemoryRepository;

    @MockitoBean
    protected ChatMemory chatMemory;

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        Mockito.reset(oAuth2JWKSService, ltiPlatformConfigurationRepository, competencyProgressService, competencyProgressApi);
        if (chatModel != null) {
            Mockito.reset(chatModel);
        }
        if (chatClient != null) {
            Mockito.reset(chatClient);
        }
        if (chatMemoryRepository != null) {
            Mockito.reset(chatMemoryRepository);
        }
        if (chatMemory != null) {
            Mockito.reset(chatMemory);
        }
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

    @BeforeEach
    protected void setupSpringAIMocks() {
        if (chatModel != null) {
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mocked AI response for testing")))));
        }
    }

}
