package de.tum.in.www1.artemis.programmingexercise;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;
import de.tum.in.www1.artemis.util.Verifiable;

public interface MockDelegate {

    void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws Exception;

    List<Verifiable> mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported) throws IOException, URISyntaxException;

    List<Verifiable> mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists)
            throws IOException, URISyntaxException;

    void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException;

    void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String bambooRepoName, String bitbucketRepoName, List<String> triggeredBy)
            throws IOException, URISyntaxException;

    void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws URISyntaxException;

    void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username, HttpStatus status) throws URISyntaxException, IOException;

    void mockRepositoryWritePermissions(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws URISyntaxException;

    void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) throws MalformedURLException, URISyntaxException, JsonProcessingException;

    void mockGetBuildLogs(ProgrammingExerciseStudentParticipation participation, List<BambooBuildResultDTO.BambooBuildLogEntryDTO> logs)
            throws URISyntaxException, JsonProcessingException;

    void mockGetRepositorySlugFromUrl(String repositorySlug, URL url);

    void mockGetProjectKeyFromUrl(String projectKey, URL url);

    void mockGetProjectKeyFromAnyUrl(String projectKey);

    void resetMockProvider();
}
