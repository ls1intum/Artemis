package de.tum.in.www1.artemis.service.connectors.hades;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_HADES;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ci.CIPermission;
import de.tum.in.www1.artemis.service.connectors.hades.dto.HadesBuildResultNotificationDTO;
import de.tum.in.www1.artemis.web.rest.dto.CheckoutDirectoriesDTO;

@Service
@Profile(PROFILE_HADES)
public class HadesCIService extends AbstractContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(HadesCIService.class);

    private final ObjectMapper objectMapper;

    public HadesCIService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri) {

    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
    }

    @Override
    public String copyBuildPlan(ProgrammingExercise sourceExercise, String sourcePlanName, ProgrammingExercise targetExercise, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        return targetExercise.getProjectKey() + "-" + targetPlanName.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String branch) {
    }

    @Override
    public void deleteProject(String projectKey) {
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {
    }

    @Override
    public String getPlanKey(Object requestBody) throws ContinuousIntegrationException {
        var dto = objectMapper.convertValue(requestBody, HadesBuildResultNotificationDTO.class);

        log.debug("Received build result for job {} in Hades", dto.getJobName());

        return dto.getJobName();
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        log.warn("Hades does not support build status retrieval");
        return null;
    }

    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        return true;
    }

    @Override
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        log.debug("Hades does not support artifact retrieval");
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        log.debug("Hades does not support project existence checks");
        return null;
    }

    @Override
    public void enablePlan(String projectKey, String planKey) {
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            String newBranch) {
        log.debug("Hades does not support plan repository updates");
    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions) {
        log.debug("Hades does not support project permissions");
    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        log.debug("Hades does not support plan permissions");
    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {
        log.debug("Hades does not support project permissions");
    }

    @Override
    public ConnectorHealth health() {
        // TODO: implement Health check
        return null;
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException {
        log.debug("Hades does not support project creation");
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        return Optional.empty();
    }

    @Override
    public CheckoutDirectoriesDTO getCheckoutDirectories(ProgrammingLanguage programmingLanguage, boolean checkoutSolution) {
        return null;
    }
}
