package de.tum.cit.aet.artemis.programming.service.hades;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HADES;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ci.AbstractContinuousIntegrationResultService;
import de.tum.cit.aet.artemis.programming.service.hades.dto.HadesTestResultsDTO;

@Lazy
@Service
@Profile(PROFILE_HADES)
public class HadesResultService extends AbstractContinuousIntegrationResultService {

    public HadesResultService(ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        super(testCaseRepository, feedbackCreationService, programmingExerciseBuildConfigRepository);
    }

    @Override
    public BuildResultNotification convertBuildResult(Object requestBody) {
        return HadesTestResultsDTO.convert(requestBody);
    }
}
