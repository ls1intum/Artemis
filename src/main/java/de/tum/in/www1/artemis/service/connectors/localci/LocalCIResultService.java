package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.service.connectors.AbstractContinuousIntegrationResultService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

/**
 * Service implementation for Local CI.
 */
@Service
@Profile("localci")
public class LocalCIResultService extends AbstractContinuousIntegrationResultService {

    public LocalCIResultService(FeedbackRepository feedbackRepository, TestwiseCoverageService testwiseCoverageService,
            BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository) {
        super(feedbackRepository, buildLogStatisticsEntryRepository, testwiseCoverageService);
    }

    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {
        // Not yet implemented for local CI.
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        if (!(requestBody instanceof LocalCIBuildResult localCIBuildResult)) {
            throw new LocalCIException("The request body is not of type LocalCIBuildResult");
        }
        return localCIBuildResult;
    }
}
