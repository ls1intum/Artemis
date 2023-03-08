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
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.AbstractContinuousIntegrationResultService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

@Service
@Profile("localci")
public class LocalCIResultService extends AbstractContinuousIntegrationResultService {

    public LocalCIResultService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            TestwiseCoverageService testwiseCoverageService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, buildLogStatisticsEntryRepository, testwiseCoverageService);
    }

    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {
        // TODO: Empty implementation to allow usage of 'localvc' with 'localci' in testing.
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        if (!(requestBody instanceof LocalCIBuildResultNotificationDTO localCIBuildResult)) {
            throw new LocalCIException("The request body is not of type LocalCIBuildResultNotificationDTO");
        }
        return localCIBuildResult;
    }
}
