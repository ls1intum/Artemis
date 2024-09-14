package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.domain.BuildLogEntry;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.dto.AbstractBuildResultNotificationDTO;
import de.tum.cit.aet.artemis.programming.repository.BuildLogStatisticsEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ci.AbstractContinuousIntegrationResultService;
import de.tum.cit.aet.artemis.programming.service.hestia.TestwiseCoverageService;

/**
 * Service implementation for integrated CI.
 */
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIResultService extends AbstractContinuousIntegrationResultService {

    public LocalCIResultService(TestwiseCoverageService testwiseCoverageService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        super(testCaseRepository, buildLogStatisticsEntryRepository, testwiseCoverageService, feedbackCreationService, programmingExerciseBuildConfigRepository);
    }

    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {
        // TODO LOCALVC_CI: Implement build logs for local CI.
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        if (!(requestBody instanceof BuildResult buildResult)) {
            throw new LocalCIException("The request body is not of type LocalCIBuildResult");
        }
        return buildResult;
    }
}
