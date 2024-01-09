package de.tum.in.www1.artemis.service.connectors.hades;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractContinuousIntegrationResultService;
import de.tum.in.www1.artemis.service.connectors.hades.dto.HadesCIBuildResultDTO;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseFeedbackCreationService;

@Profile("hades")
@Service
public class HadesCIResultService extends AbstractContinuousIntegrationResultService {

    private static final Logger log = LoggerFactory.getLogger(HadesCIResultService.class);

    private final ObjectMapper mapper;

    public HadesCIResultService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            TestwiseCoverageService testwiseCoverageService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseTestCaseRepository testCaseRepository, ObjectMapper mapper) {
        super(programmingSubmissionRepository, feedbackRepository, testCaseRepository, buildLogService, buildLogStatisticsEntryRepository, testwiseCoverageService,
                feedbackCreationService);
        this.mapper = mapper;
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        log.debug("Received build result from Hades CI: {}", requestBody);
        return mapper.convertValue(requestBody, HadesCIBuildResultDTO.class);
    }

    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {

    }
}
