package de.tum.in.www1.artemis.programmingexercise;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;
import de.tum.in.www1.artemis.util.Verifiable;

public interface MockDelegate {

    void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws Exception;

    List<Verifiable> mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans) throws Exception;

    List<Verifiable> mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception;

    void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException;

    void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy)
            throws IOException, URISyntaxException;

    void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws Exception;

    void mockForkRepositoryForParticipation(ProgrammingExercise exercise, String username, HttpStatus status) throws URISyntaxException, IOException;

    void mockRepositoryWritePermissions(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws Exception;

    void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) throws MalformedURLException, URISyntaxException, JsonProcessingException;

    void mockGetBuildLogs(ProgrammingExerciseStudentParticipation participation, List<BambooBuildResultDTO.BambooBuildLogEntryDTO> logs)
            throws URISyntaxException, JsonProcessingException;

    void mockGetRepositorySlugFromUrl(String repositorySlug, VcsRepositoryUrl url);

    void mockGetProjectKeyFromUrl(String projectKey, VcsRepositoryUrl url);

    void mockGetProjectKeyFromAnyUrl(String projectKey);

    void resetMockProvider();
}
