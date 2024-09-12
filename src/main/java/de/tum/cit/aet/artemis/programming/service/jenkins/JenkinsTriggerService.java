package de.tum.cit.aet.artemis.programming.service.jenkins;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.jenkins.build_plan.JenkinsBuildPlanService;

@Profile("jenkins")
@Service
public class JenkinsTriggerService implements ContinuousIntegrationTriggerService {

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    public JenkinsTriggerService(JenkinsBuildPlanService jenkinsBuildPlanService) {
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        jenkinsBuildPlanService.triggerBuild(projectKey, planKey);
    }
}
