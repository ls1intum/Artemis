package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

public abstract class AbstractContinuousIntegrationService implements ContinuousIntegrationService {

    protected final ProgrammingSubmissionRepository programmingSubmissionRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final BuildLogEntryService buildLogService;

    protected final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    protected final TestwiseCoverageService testwiseCoverageService;

    public AbstractContinuousIntegrationService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository,
            BuildLogEntryService buildLogService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, TestwiseCoverageService testwiseCoverageService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
        this.buildLogService = buildLogService;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
        this.testwiseCoverageService = testwiseCoverageService;
    }
}
