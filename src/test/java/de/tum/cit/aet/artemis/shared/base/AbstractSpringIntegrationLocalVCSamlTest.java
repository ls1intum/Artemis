package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.ATLAS_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SAML2;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

@ResourceLock("AbstractSpringIntegrationLocalVCSamlTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_ARTEMIS, PROFILE_CORE, PROFILE_LOCALVC, PROFILE_LOCALCI, PROFILE_SAML2, PROFILE_SCHEDULING, PROFILE_LTI, "local" })
@TestPropertySource(properties = { ATLAS_ENABLED_PROPERTY_NAME + "=false", "artemis.user-management.use-external=false",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_localvc_saml", "artemis.version-control.ssh-port=1237" })
public abstract class AbstractSpringIntegrationLocalVCSamlTest extends AbstractArtemisIntegrationTest {

    @Autowired
    protected PasswordService passwordService;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    protected Path localVCBasePath;

    // NOTE: this has to be a MockitoBean, because the class cannot be instantiated in the tests
    @MockitoBean
    protected RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    @AfterEach
    void reset() {
        Mockito.reset(relyingPartyRegistrationRepository, mailService);
        super.resetSpyBeans();
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject, boolean useCustomBuildPlanDefinition, boolean useCustomBuildPlanWorked) {
        // saml2-specific mocks
        doReturn(null).when(relyingPartyRegistrationRepository).findByRegistrationId(anyString());
        doNothing().when(mailService).sendSAML2SetPasswordMail(any(User.class));
    }

    @Override
    public void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) {
        mockConnectorRequestsForSetup(exerciseForImport, false, false, false);
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos) {
        // Not needed for this test
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) {
        // Not needed for this test
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        // Not needed for this test
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Not needed for this test
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs) {
        // Not needed for this test
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Not needed for this test
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Not needed for this test
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) {
        // Not needed for this test
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) {
        // Not needed for this test
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) {
        // Not needed for this test
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) {
        // Not needed for this test
    }

    @Override
    public void resetMockProvider() {
        // Not needed for this test
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) {
        // Not needed for this test
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) {
        // Not needed for this test
    }

    @Override
    public void mockGetBuildPlanConfig(String projectKey, String planName) {
        // Not needed for this test
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) {
        // Not needed for this test
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) {
        // Not needed for this test
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) throws Exception {
        // Not needed for this test
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        // Not needed for this test
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        // Not needed for this test
    }

    @Override
    public void mockGetCiProjectMissing(ProgrammingExercise exercise) {
        // Not needed for this test
    }
}
