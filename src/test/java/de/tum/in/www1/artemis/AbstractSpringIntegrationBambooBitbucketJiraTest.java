package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.ActiveProfiles;

import com.atlassian.bamboo.specs.util.BambooServer;

import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.BitbucketBambooUpdateService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooRepositoryDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooTriggerDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketService;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketRepositoryDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;
import de.tum.in.www1.artemis.util.*;

@SpringBootTest(properties = { "artemis.athene.token-validity-in-seconds=10800",
        "artemis.athene.base64-secret=YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=" })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ "artemis", "bamboo", "bitbucket", "jira", "ldap", "scheduling", "athene" })
public abstract class AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Value("${server.url}")
    protected String ARTEMIS_SERVER_URL;

    @SpyBean
    protected LdapUserService ldapUserService;

    // NOTE: we prefer SpyBean over MockBean, because it is more lightweight, we can mock method, but we can also invoke actual methods during testing
    @SpyBean
    protected LtiService ltiService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Bamboo using the corresponding RestTemplate.
    @SpyBean
    protected BitbucketBambooUpdateService continuousIntegrationUpdateService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Bamboo using the corresponding RestTemplate.
    @SpyBean
    protected BambooService continuousIntegrationService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Bitbucket using the corresponding RestTemplate.
    @SpyBean
    protected BitbucketService versionControlService;

    @SpyBean
    protected BambooServer bambooServer;

    @SpyBean
    protected GitService gitService;

    @SpyBean
    protected GroupNotificationService groupNotificationService;

    @SpyBean
    protected WebsocketMessagingService websocketMessagingService;

    @SpyBean
    protected PlantUmlService plantUmlService;

    @SpyBean
    protected SimpMessageSendingOperations messagingTemplate;

    @SpyBean
    protected ProgrammingSubmissionService programmingSubmissionService;

    @SpyBean
    protected ExamAccessService examAccessService;

    @SpyBean
    protected InstanceMessageSendService instanceMessageSendService;

    @SpyBean
    protected ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    @SpyBean
    protected ProgrammingExerciseParticipationService programmingExerciseParticipationServiceSpy;

    @Autowired
    protected BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    protected BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    @AfterEach
    public void resetSpyBeans() {
        Mockito.reset(ltiService, continuousIntegrationService, versionControlService, bambooServer, gitService, groupNotificationService, websocketMessagingService,
                plantUmlService, messagingTemplate, programmingSubmissionService, examAccessService, instanceMessageSendService, programmingExerciseScheduleService,
                programmingExerciseParticipationServiceSpy);
    }

    protected List<Verifiable> mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users) throws IOException, URISyntaxException {
        final var verifications = new LinkedList<Verifiable>();
        bitbucketRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        bitbucketRequestMockProvider.mockConfigureRepository(exercise, username, users);
        bambooRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        mockUpdatePlanRepositoryForParticipation(exercise, username);
        bambooRequestMockProvider.mockEnablePlan(exercise.getProjectKey(), username);
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        return verifications;
    }

    protected void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var bitbucketRepoName = projectKey.toLowerCase() + "-" + username;

        mockUpdatePlanRepository(exercise, username, ASSIGNMENT_REPO_NAME, bitbucketRepoName, List.of());
    }

    private void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String bambooRepoName, String bitbucketRepoName, List<String> triggeredBy)
            throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var buildPlanKey = (projectKey + "-" + planName).toUpperCase();

        final var bambooRepositoryAssignment = new BambooRepositoryDTO(296200357L, ASSIGNMENT_REPO_NAME);
        final var bambooRepositoryTests = new BambooRepositoryDTO(296200356L, TEST_REPO_NAME);
        final var bitbucketRepository = new BitbucketRepositoryDTO("id", bitbucketRepoName, projectKey, "ssh:cloneUrl");

        bambooRequestMockProvider.mockGetBuildPlanRepositoryList(buildPlanKey);

        bitbucketRequestMockProvider.mockGetBitbucketRepository(exercise, bitbucketRepoName, bitbucketRepository);

        var applicationLinksToBeReturned = bambooRequestMockProvider.createApplicationLink();
        var applicationLink = applicationLinksToBeReturned.getApplicationLinks().get(0);
        bambooRequestMockProvider.mockGetApplicationLinks(applicationLinksToBeReturned);

        if (ASSIGNMENT_REPO_NAME.equals(bambooRepoName)) {
            bambooRequestMockProvider.mockUpdateRepository(buildPlanKey, bambooRepositoryAssignment, bitbucketRepository, applicationLink);
        }
        else {
            bambooRequestMockProvider.mockUpdateRepository(buildPlanKey, bambooRepositoryTests, bitbucketRepository, applicationLink);
        }

        if (!triggeredBy.isEmpty()) {
            // in case there are triggers
            List<BambooTriggerDTO> triggerList = bambooRequestMockProvider.mockGetTriggerList(buildPlanKey);

            for (var trigger : triggerList) {
                bambooRequestMockProvider.mockDeleteTrigger(buildPlanKey, trigger.getId());
            }

            for (var ignored : triggeredBy) {
                // we only support one specific case for the repository above here
                bambooRequestMockProvider.mockAddTrigger(buildPlanKey, bambooRepositoryAssignment.getId().toString());
            }
        }
    }

    protected void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        bambooRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exercise);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        bambooRequestMockProvider.mockRemoveAllDefaultProjectPermissions(exercise);
        bambooRequestMockProvider.mockGiveProjectPermissions(exercise);

        // TODO: check the actual plan and plan permissions that get passed here
        doReturn(null).when(bambooServer).publish(any());
    }

    protected List<Verifiable> mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported) throws IOException, URISyntaxException {
        final var verifications = new ArrayList<Verifiable>();
        final var projectKey = exerciseToBeImported.getProjectKey();
        final var sourceProjectKey = sourceExercise.getProjectKey();
        final var templateRepoName = (projectKey + "-" + RepositoryType.TEMPLATE.getName()).toLowerCase();
        final var solutionRepoName = (projectKey + "-" + RepositoryType.SOLUTION.getName()).toLowerCase();
        final var testsRepoName = (projectKey + "-" + RepositoryType.TESTS.getName()).toLowerCase();
        var nextParticipationId = sourceExercise.getTemplateParticipation().getId() + 1;
        final var artemisSolutionHookPath = ARTEMIS_SERVER_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + nextParticipationId++;
        final var artemisTemplateHookPath = ARTEMIS_SERVER_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + nextParticipationId;
        final var artemisTestsHookPath = ARTEMIS_SERVER_URL + TEST_CASE_CHANGED_API_PATH + (sourceExercise.getId() + 1);

        bambooRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false);
        bambooRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), TEMPLATE.getName(), projectKey, TEMPLATE.getName(), false);
        bambooRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), SOLUTION.getName(), projectKey, SOLUTION.getName(), true);
        doReturn(null).when(bambooServer).publish(any());
        bambooRequestMockProvider.mockGiveProjectPermissions(exerciseToBeImported);
        bambooRequestMockProvider.mockEnablePlan(projectKey, TEMPLATE.getName());
        bambooRequestMockProvider.mockEnablePlan(projectKey, SOLUTION.getName());
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported);
        bitbucketRequestMockProvider.mockCopyRepository(sourceProjectKey, projectKey, sourceExercise.getTemplateRepositoryName(), templateRepoName);
        bitbucketRequestMockProvider.mockCopyRepository(sourceProjectKey, projectKey, sourceExercise.getSolutionRepositoryName(), solutionRepoName);
        bitbucketRequestMockProvider.mockCopyRepository(sourceProjectKey, projectKey, sourceExercise.getTestRepositoryName(), testsRepoName);
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, templateRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, templateRepoName, artemisTemplateHookPath);
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, solutionRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, solutionRepoName, artemisSolutionHookPath);
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, testsRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, testsRepoName, artemisTestsHookPath);
        mockUpdatePlanRepository(exerciseToBeImported, TEMPLATE.getName(), ASSIGNMENT_REPO_NAME, templateRepoName, List.of(ASSIGNMENT_REPO_NAME));
        mockUpdatePlanRepository(exerciseToBeImported, TEMPLATE.getName(), TEST_REPO_NAME, testsRepoName, List.of());
        mockUpdatePlanRepository(exerciseToBeImported, SOLUTION.getName(), ASSIGNMENT_REPO_NAME, solutionRepoName, List.of());
        mockUpdatePlanRepository(exerciseToBeImported, SOLUTION.getName(), TEST_REPO_NAME, testsRepoName, List.of());
        bambooRequestMockProvider.mockTriggerBuild(exerciseToBeImported.getProjectKey() + "-" + TEMPLATE.getName());
        bambooRequestMockProvider.mockTriggerBuild(exerciseToBeImported.getProjectKey() + "-" + SOLUTION.getName());
        return verifications;
    }

    /**
     * can be invoked for teams and students
     */
    protected void setupRepositoryMocksParticipant(ProgrammingExercise exercise, String participantName, LocalRepository studentRepo) throws Exception {
        final var projectKey = exercise.getProjectKey();
        String participantRepoName = projectKey.toLowerCase() + "-" + participantName;
        var participantRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentRepo.originRepoFile);
        doReturn(participantRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, participantRepoName);
        doReturn(gitService.getRepositoryByLocalPath(studentRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(participantRepoTestUrl.getURL(), true);
        doReturn(participantRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(participantRepoTestUrl.getURL());
        doReturn(projectKey).when(continuousIntegrationService).getProjectKeyFromUrl(participantRepoTestUrl.getURL());
        doReturn(participantRepoName).when(versionControlService).getRepositorySlugFromUrl(participantRepoTestUrl.getURL());
    }

    protected void setupRepositoryMocks(ProgrammingExercise exercise, LocalRepository exerciseRepo, LocalRepository solutionRepo, LocalRepository testRepo) throws Exception {
        final var projectKey = exercise.getProjectKey();

        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();

        var exerciseRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(exerciseRepo.originRepoFile);
        var testRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.originRepoFile);
        var solutionRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, solutionRepoName);

        doReturn(gitService.getRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(exerciseRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(solutionRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(solutionRepoTestUrl.getURL(), true);

        doReturn(exerciseRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());

        doReturn(projectKey).when(continuousIntegrationService).getProjectKeyFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(projectKey).when(continuousIntegrationService).getProjectKeyFromUrl(testRepoTestUrl.getURL());
        doReturn(projectKey).when(continuousIntegrationService).getProjectKeyFromUrl(solutionRepoTestUrl.getURL());

        doReturn(exerciseRepoName).when(versionControlService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(versionControlService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(versionControlService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());

        doReturn(projectKey).when(versionControlService).getProjectKeyFromUrl(any());
    }
}
