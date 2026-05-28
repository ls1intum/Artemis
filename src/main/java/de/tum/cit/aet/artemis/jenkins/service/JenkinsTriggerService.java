package de.tum.cit.aet.artemis.jenkins.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.jenkins.service.build_plan.JenkinsBuildPlanService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;

@Profile(PROFILE_JENKINS)
@Lazy
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
