package de.tum.cit.aet.artemis.programming.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;

import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

public interface MockDelegate {

    void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject, boolean useCustomBuildPlanDefinition, boolean useCustomBuildPlanWorked)
            throws Exception;

    void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos)
            throws Exception;

    void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) throws Exception;

    void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) throws Exception;

    void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception;

    void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception;

    void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException;

    void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs) throws IOException, URISyntaxException;

    @Deprecated
    void mockGetRepositorySlugFromRepositoryUri(String repositorySlug, VcsRepositoryUri repositoryUri);

    @Deprecated
    void mockGetProjectKeyFromRepositoryUri(String projectKey, VcsRepositoryUri repositoryUri);

    @Deprecated
    void mockGetRepositoryPathFromRepositoryUri(String projectPath, VcsRepositoryUri repositoryUri);

    @Deprecated
    void mockGetProjectKeyFromAnyUrl(String projectKey);

    void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void resetMockProvider() throws Exception;

    void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) throws Exception;

    void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) throws Exception;

    void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) throws Exception;

    void mockGetBuildPlanConfig(String projectKey, String planName) throws Exception;

    void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws Exception;

    void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) throws Exception;

    void mockCheckIfBuildPlanExists(String projectKey, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) throws Exception;

    void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception;

    void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception;

    void mockGetCiProjectMissing(ProgrammingExercise exercise) throws IOException;
}
