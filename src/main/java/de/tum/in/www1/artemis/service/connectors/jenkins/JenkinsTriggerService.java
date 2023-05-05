package de.tum.in.www1.artemis.service.connectors.jenkins;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;

@Profile("jenkins")
@Service
public class JenkinsTriggerService implements ContinuousIntegrationTriggerService {

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    public JenkinsTriggerService(JenkinsBuildPlanService jenkinsBuildPlanService) {
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
    }

    /**
     * Trigger a build on the Jenkins continuous integration server for the given participation.
     *
     * @param participation the participation with the id of the build plan that should be triggered
     * @param commitHash    the commit hash of the commit that triggers the build. It is not used here as this method is only used when the latest commit should be built, which is
     *                          what Jenkins does by default.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        jenkinsBuildPlanService.triggerBuild(projectKey, planKey);
    }
}
