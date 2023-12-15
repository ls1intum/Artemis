package de.tum.in.www1.artemis.service.connectors.jenkins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.in.www1.artemis.service.connectors.jenkins.build_plan.JenkinsBuildPlanService;

@Profile("jenkins")
@Service
public class JenkinsTriggerService implements ContinuousIntegrationTriggerService {

    private final Logger log = LoggerFactory.getLogger(JenkinsTriggerService.class);

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    public JenkinsTriggerService(JenkinsBuildPlanService jenkinsBuildPlanService) {
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        jenkinsBuildPlanService.triggerBuild(projectKey, planKey);
    }
}
