package de.tum.cit.aet.artemis.programming.service.jenkins;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.JenkinsException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.ci.notification.dto.TestResultsDTO;
import de.tum.cit.aet.artemis.programming.service.jenkins.build_plan.JenkinsBuildPlanService;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobService;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.dto.BuildTriggerRequestDTO;

@Profile(PROFILE_JENKINS)
@Lazy
@Service
public class JenkinsService implements ContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${artemis.continuous-integration.url}")
    private URI jenkinsServerUri;

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    private final JenkinsJobService jenkinsJobService;

    private final RestTemplate shortTimeoutRestTemplate;

    private final Optional<AeolusTemplateService> aeolusTemplateService;

    private final ProfileService profileService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public JenkinsService(@Qualifier("shortTimeoutJenkinsRestTemplate") RestTemplate shortTimeoutRestTemplate, JenkinsBuildPlanService jenkinsBuildPlanService,
            JenkinsJobService jenkinsJobService, Optional<AeolusTemplateService> aeolusTemplateService, ProfileService profileService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
        this.jenkinsJobService = jenkinsJobService;
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

        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, BuildPlanType.TEMPLATE.getName(), exercise.getRepositoryURI(RepositoryType.TEMPLATE));
        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, BuildPlanType.SOLUTION.getName(), exercise.getRepositoryURI(RepositoryType.SOLUTION));
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
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {
        jenkinsBuildPlanService.configureBuildPlanForParticipation(participation);
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUri, String existingRepoUri,
            String newDefaultBranch) {
        jenkinsBuildPlanService.updateBuildPlanRepositories(buildProjectKey, buildPlanKey, newRepoUri, existingRepoUri);
    }

    @Override
    public void deleteProject(String projectKey) {
        jenkinsJobService.deleteFolderJob(projectKey);
    }

    @Override
    public void deleteBuildPlan(String projectKey, String planKey) {
        jenkinsJobService.deleteJob(projectKey, planKey);
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
    public String checkIfProjectExists(String projectKey, String projectName) {
        try {
            final var job = jenkinsJobService.getFolderJob(projectKey);
            if (job == null || job.url() == null || job.url().isEmpty()) {
                // means the project does not exist
                return null;
            }
            else {
                return "The project " + projectKey + " already exists in Jenkins. Please choose a different short name!";
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
    public ConnectorHealth health() {
        Map<String, Object> additionalInfo = Map.of("url", jenkinsServerUri);
        try {
            URI uri = JenkinsEndpoints.HEALTH.buildEndpoint(jenkinsServerUri).build(true).toUri();
            // Note: we simply check if the login page is reachable
            shortTimeoutRestTemplate.getForObject(uri, String.class);
            return new ConnectorHealth(true, additionalInfo);
        }
        catch (Exception emAll) {
            return new ConnectorHealth(false, additionalInfo, new JenkinsException("Jenkins Server is down!"));
        }
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException {
        try {
            jenkinsJobService.createFolder(programmingExercise.getProjectKey());
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error creating folder for exercise " + programmingExercise, e);
        }
    }

    @Override
    public UUID build(BuildTriggerRequestDTO buildTriggerRequestDTO) throws ContinuousIntegrationException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'build'");
    }
}
