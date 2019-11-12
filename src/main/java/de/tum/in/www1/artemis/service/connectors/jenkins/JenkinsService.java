package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URL;
import java.util.List;

import org.apache.http.HttpException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;

@Service
public class JenkinsService implements ContinuousIntegrationService {

    private final RestTemplate restTemplate;

    public JenkinsService(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, String repositoryName, String testRepositoryName) {

    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName) {
        return null;
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {

    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws HttpException {

    }

    @Override
    public void deleteProject(String projectKey) {

    }

    @Override
    public void deleteBuildPlan(String buildPlanId) {

    }

    @Override
    public Result onBuildCompletedOld(ProgrammingExerciseParticipation participation) {
        return null;
    }

    @Override
    public String getPlanKey(Object requestBody) throws Exception {
        return null;
    }

    @Override
    public Result onBuildCompletedNew(ProgrammingExerciseParticipation participation, Object requestBody) throws Exception {
        return null;
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        return null;
    }

    @Override
    public Boolean buildPlanIdIsValid(String buildPlanId) {
        return null;
    }

    @Override
    public List<Feedback> getLatestBuildResultDetails(Result result) {
        return null;
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(String buildPlanId) {
        return null;
    }

    @Override
    public URL getBuildPlanWebUrl(ProgrammingExerciseParticipation participation) {
        return null;
    }

    @Override
    public ResponseEntity retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        return null;
    }

    @Override
    public boolean isBuildPlanEnabled(String planId) {
        return false;
    }

    @Override
    public String enablePlan(String planKey) {
        return null;
    }

    @Override
    public void updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String repoProjectName, String repoName) {

    }
}
