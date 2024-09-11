package de.tum.cit.aet.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbytwo.jenkins.JenkinsServer;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.JenkinsException;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.enumeration.BuildPlanType;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.enumeration.RepositoryType;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.service.connectors.aeolus.Windfile;
import de.tum.cit.aet.artemis.service.connectors.ci.AbstractContinuousIntegrationService;
import de.tum.cit.aet.artemis.service.connectors.ci.CIPermission;
import de.tum.cit.aet.artemis.service.connectors.ci.notification.dto.TestResultsDTO;
import de.tum.cit.aet.artemis.service.connectors.jenkins.build_plan.JenkinsBuildPlanService;
import de.tum.cit.aet.artemis.service.connectors.jenkins.jobs.JenkinsJobService;
import de.tum.cit.aet.artemis.web.rest.dto.CheckoutDirectoriesDTO;

@Profile("jenkins")
@Service
public class JenkinsService extends AbstractContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${artemis.continuous-integration.url}")
    protected URL serverUrl;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    private final JenkinsServer jenkinsServer;

    private final JenkinsJobService jenkinsJobService;

    private final JenkinsInternalUrlService jenkinsInternalUrlService;

    private final RestTemplate shortTimeoutRestTemplate;

    private final Optional<AeolusTemplateService> aeolusTemplateService;

    private final ProfileService profileService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public JenkinsService(JenkinsServer jenkinsServer, @Qualifier("shortTimeoutJenkinsRestTemplate") RestTemplate shortTimeoutRestTemplate,
            JenkinsBuildPlanService jenkinsBuildPlanService, JenkinsJobService jenkinsJobService, JenkinsInternalUrlService jenkinsInternalUrlService,
            Optional<AeolusTemplateService> aeolusTemplateService, ProfileService profileService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.jenkinsServer = jenkinsServer;
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
        this.jenkinsJobService = jenkinsJobService;
        this.jenkinsInternalUrlService = jenkinsInternalUrlService;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.aeolusTemplateService = aeolusTemplateService;
        this.profileService = profileService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri) {
        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, planKey, repositoryUri);
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) throws JsonProcessingException {
        final String projectKey = exercise.getProjectKey();

        if (!jenkinsBuildPlanService.projectFolderExists(projectKey)) {
            createProjectForExercise(exercise);
        }

        deleteBuildPlan(projectKey, exercise.getTemplateBuildPlanId());
        deleteBuildPlan(projectKey, exercise.getSolutionBuildPlanId());

        if (exercise.getBuildConfig().getBuildPlanConfiguration() != null) {
            resetCustomBuildPlanToTemplate(exercise);
        }

        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, BuildPlanType.TEMPLATE.getName(), exercise.getRepositoryURL(RepositoryType.TEMPLATE));
        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, BuildPlanType.SOLUTION.getName(), exercise.getRepositoryURL(RepositoryType.SOLUTION));
    }

    /**
     * Reset the custom build plan to the template build plan configuration provided by the Aeolus template service.
     *
     * @param exercise the programming exercise for which the build plan should be reset
     */
    private void resetCustomBuildPlanToTemplate(ProgrammingExercise exercise) throws JsonProcessingException {
        if (aeolusTemplateService.isEmpty()) {
            return;
        }
        Windfile windfile = aeolusTemplateService.get().getDefaultWindfileFor(exercise);
        if (windfile != null) {
            exercise.getBuildConfig().setBuildPlanConfiguration(mapper.writeValueAsString(windfile));
        }
        if (profileService.isAeolusActive()) {
            programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
        }
    }

    @Override
    public String copyBuildPlan(ProgrammingExercise sourceExercise, String sourcePlanName, ProgrammingExercise targetExercise, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        // Make sure the build config is loaded
        programmingExerciseBuildConfigRepository.loadAndSetBuildConfig(sourceExercise);
        programmingExerciseBuildConfigRepository.loadAndSetBuildConfig(targetExercise);
        return jenkinsBuildPlanService.copyBuildPlan(sourceExercise, sourcePlanName, targetExercise, targetPlanName);
    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        jenkinsBuildPlanService.givePlanPermissions(programmingExercise, planName);
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String branch) {
        jenkinsBuildPlanService.configureBuildPlanForParticipation(participation);
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUri, String existingRepoUri,
            String newDefaultBranch) {
        jenkinsBuildPlanService.updateBuildPlanRepositories(buildProjectKey, buildPlanKey, newRepoUri, existingRepoUri);
    }

    @Override
    public void deleteProject(String projectKey) {
        jenkinsJobService.deleteJob(projectKey);
    }

    @Override
    public void deleteBuildPlan(String projectKey, String planKey) {
        jenkinsBuildPlanService.deleteBuildPlan(projectKey, planKey);
    }

    @Override
    public String getPlanKey(Object requestBody) throws JenkinsException {
        try {
            TestResultsDTO dto = TestResultsDTO.convert(requestBody);
            return jenkinsBuildPlanService.getBuildPlanKeyFromTestResults(dto);
        }
        catch (JsonProcessingException jsonProcessingException) {
            throw new JenkinsException("Something went wrong trying to parse the requestBody while getting the PlanKey from Jenkins!");
        }
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        var urlString = serverUrl + "/project/" + projectKey + "/" + buildPlanId;
        return Optional.of(jenkinsInternalUrlService.toInternalCiUrl(urlString));
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        if (participation.getBuildPlanId() == null) {
            // The build plan does not exist, the build status cannot be retrieved
            return null;
        }

        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        return jenkinsBuildPlanService.getBuildStatusOfPlan(projectKey, planKey);
    }

    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        return jenkinsBuildPlanService.buildPlanExists(projectKey, buildPlanId);
    }

    @Override
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        // TODO, not necessary for the core functionality
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        try {
            final var job = jenkinsServer.getJob(projectKey);
            if (job == null || job.getUrl() == null || job.getUrl().isEmpty()) {
                // means the project does not exist
                return null;
            }
            else {
                return "The project " + projectKey + " already exists in the CI Server. Please choose a different short name!";
            }
        }
        catch (Exception emAll) {
            log.warn(emAll.getMessage());
            // in case of an error message, we assume the project exist
            return "The project already exists on the Continuous Integration Server. Please choose a different title and short name!";
        }
    }

    @Override
    public void enablePlan(String projectKey, String planKey) {
        jenkinsBuildPlanService.enablePlan(projectKey, planKey);
    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions) {
        // Not needed since Jenkins doesn't support project permissions
    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {
        // Not needed since Jenkins doesn't support project permissions
    }

    @Override
    public ConnectorHealth health() {
        Map<String, Object> additionalInfo = Map.of("url", serverUrl);
        try {
            // Note: we simply check if the login page is reachable
            shortTimeoutRestTemplate.getForObject(serverUrl + "/login", String.class);
            return new ConnectorHealth(true, additionalInfo);
        }
        catch (Exception emAll) {
            return new ConnectorHealth(false, additionalInfo, new JenkinsException("Jenkins Server is down!"));
        }
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException {
        try {
            jenkinsServer.createFolder(programmingExercise.getProjectKey(), useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error creating folder for exercise " + programmingExercise, e);
        }
    }

    @Override
    public CheckoutDirectoriesDTO getCheckoutDirectories(ProgrammingLanguage programmingLanguage, boolean checkoutSolution) {
        throw new UnsupportedOperationException("Method not implemented, consult the build plans in Jenkins for more information on the checkout directories.");
    }
}
