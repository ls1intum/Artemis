package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ci.AbstractContinuousIntegrationResultService;

/**
 * Service implementation for integrated CI.
 */
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIResultService extends AbstractContinuousIntegrationResultService {

    public LocalCIResultService(ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        super(testCaseRepository, feedbackCreationService, programmingExerciseBuildConfigRepository);
    }

    @Override
    public BuildResultNotification convertBuildResult(Object requestBody) {
        if (!(requestBody instanceof BuildResult buildResult)) {
            throw new LocalCIException("The request body is not of type LocalCIBuildResult");
        }
        return buildResult;
    }
}
