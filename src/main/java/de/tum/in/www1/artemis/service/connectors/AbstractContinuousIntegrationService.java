package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

public abstract class AbstractContinuousIntegrationService implements ContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(AbstractContinuousIntegrationService.class);

    @Value("${artemis.continuous-integration.url}")
    protected URL serverUrl;

    protected final ProgrammingSubmissionRepository programmingSubmissionRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final BuildLogEntryService buildLogService;

    protected final RestTemplate restTemplate;

    protected final RestTemplate shortTimeoutRestTemplate;

    public AbstractContinuousIntegrationService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository,
            BuildLogEntryService buildLogService, RestTemplate restTemplate, RestTemplate shortTimeoutRestTemplate) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.buildLogService = buildLogService;
    }

    @Override
    public Result createResultFromBuildResult(AbstractBuildResultNotificationDTO buildResult, ProgrammingExerciseParticipation participation) {
        final var result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful(buildResult.isBuildSuccessful());
        result.setCompletionDate(buildResult.getBuildRunDate());
        result.setScore(buildResult.getBuildScore(), participation.getProgrammingExercise().getCourseViaExerciseGroupOrCourseMember());
        result.setParticipation((Participation) participation);
        addFeedbackToResult(result, buildResult);
        return result;
    }

    /**
     * Converts build result details into feedback and stores it in the result object
     *
     * @param result      the result for which the feedback should be added
     * @param buildResult The build result
     */
    protected abstract void addFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult);
}
