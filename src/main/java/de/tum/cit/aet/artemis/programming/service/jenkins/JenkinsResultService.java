package de.tum.cit.aet.artemis.programming.service.jenkins;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ci.AbstractContinuousIntegrationResultService;
import de.tum.cit.aet.artemis.programming.service.ci.notification.dto.TestResultsDTO;

@Profile(PROFILE_JENKINS)
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
