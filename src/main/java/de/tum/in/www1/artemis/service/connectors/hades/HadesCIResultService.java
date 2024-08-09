package de.tum.in.www1.artemis.service.connectors.hades;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_HADES;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractContinuousIntegrationResultService;
import de.tum.in.www1.artemis.service.connectors.hades.dto.HadesBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseFeedbackCreationService;

@Profile(PROFILE_HADES)
@Service
public class HadesCIResultService extends AbstractContinuousIntegrationResultService {

    private static final Logger log = LoggerFactory.getLogger(HadesCIResultService.class);

    private final ObjectMapper mapper;

    public HadesCIResultService(TestwiseCoverageService testwiseCoverageService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseTestCaseRepository testCaseRepository, ObjectMapper mapper) {
        super(testCaseRepository, buildLogStatisticsEntryRepository, testwiseCoverageService, feedbackCreationService);
        this.mapper = mapper;
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        log.debug("Convert request body to BuildResultNotificationDTO");
        mapper.registerModule(new JavaTimeModule());
        return mapper.convertValue(requestBody, HadesBuildResultNotificationDTO.class);
    }

    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {
        // TODO HADES_CI: Implement build logs for Hades CI.
    }
}
