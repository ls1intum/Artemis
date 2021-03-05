package de.tum.in.www1.artemis.programmingexercise;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;

public interface MockDelegate {

    void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws Exception;

    void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans) throws Exception;

    void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists, HttpStatus status) throws Exception;

    void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception;

    void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException;

    void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy)
            throws IOException, URISyntaxException;

    void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws Exception;

    void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException, GitLabApiException;

    void mockRepositoryWritePermissions(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws Exception;

    void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) throws MalformedURLException, URISyntaxException, JsonProcessingException;

    void mockGetBuildLogs(ProgrammingExerciseStudentParticipation participation, List<BambooBuildResultDTO.BambooBuildLogEntryDTO> logs)
            throws URISyntaxException, JsonProcessingException;

    void mockGetRepositorySlugFromRepositoryUrl(String repositorySlug, VcsRepositoryUrl repositoryUrl);

    void mockGetRepositorySlugFromUrl(String repositorySlug, URL url);

    void mockGetProjectKeyFromRepositoryUrl(String projectKey, VcsRepositoryUrl repositoryUrl);

    void mockGetProjectKeyFromUrl(String projectKey, URL url);

    void mockGetProjectKeyFromAnyUrl(String projectKey);

    void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) throws URISyntaxException, JsonProcessingException;

    void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void resetMockProvider();

    void mockUpdateUserInUserManagement(String oldLogin, User user, Set<String> oldGroups) throws Exception;

    void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldTeachingAssistantGroup) throws Exception;

    void mockCreateUserInUserManagement(User user) throws Exception;

    void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement) throws Exception;
}
