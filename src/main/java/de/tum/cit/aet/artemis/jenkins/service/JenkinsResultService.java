package de.tum.cit.aet.artemis.jenkins.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.localci.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.localci.service.ci.AbstractContinuousIntegrationResultService;
import de.tum.cit.aet.artemis.localci.service.ci.notification.dto.TestResultsDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

@Profile(PROFILE_JENKINS)
@Lazy
@Service
public class JenkinsResultService extends AbstractContinuousIntegrationResultService {

    public JenkinsResultService(ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        super(testCaseRepository, feedbackCreationService, programmingExerciseBuildConfigRepository);
    }

    @Override
    public BuildResultNotification convertBuildResult(Object requestBody) {
        return TestResultsDTO.convert(requestBody);
    }
}
