package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.TextAssessmentQueueService;

@Service
@Profile("athena")
public class AthenaService {

    private final Logger log = LoggerFactory.getLogger(AthenaService.class);

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    private final TextAssessmentQueueService textAssessmentQueueService;

    private final TextBlockRepository textBlockRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    // Contains tasks submitted to Athena and currently processing
    private final List<Long> runningAthenaTasks = new ArrayList<>();

    public AthenaService(TextSubmissionRepository textSubmissionRepository, TextBlockRepository textBlockRepository, TextExerciseRepository textExerciseRepository,
            TextAssessmentQueueService textAssessmentQueueService, @Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.textBlockRepository = textBlockRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.textAssessmentQueueService = textAssessmentQueueService;
        connector = new AthenaConnector<>(log, athenaRestTemplate, ResponseDTO.class);
    }

    // region Request/Response DTOs
    private static class RequestDTO {

        public long courseId;

        public List<TextSubmission> submissions;

        RequestDTO(@NotNull long courseId, @NotNull List<TextSubmission> submissions) {
            this.courseId = courseId;
            this.submissions = createSubmissionDTOs(submissions);
        }

        /**
         * Converts TextSubmissions to DTO objects to prepare for sending them to Athena in a REST call.
         */
        @NotNull
        private static List<TextSubmission> createSubmissionDTOs(@NotNull List<TextSubmission> submissions) {
            return submissions.stream().map(textSubmission -> {
                final TextSubmission submission = new TextSubmission();
                submission.setText(textSubmission.getText());
                submission.setId(textSubmission.getId());
                return submission;
            }).toList();
        }
    }

    private static class ResponseDTO {

        public String detail;

    }
    // endregion

    /**
     * Register an Athena task for an exercise as running
     *
     * @param exerciseId the exerciseId which the Athena task is running for
     */
    public void startTask(Long exerciseId) {
        runningAthenaTasks.add(exerciseId);
    }

    /**
     * Delete an Athena task for an exercise from the running tasks
     *
     * @param exerciseId the exerciseId which the Athena task finished for
     */
    public void finishTask(Long exerciseId) {
        runningAthenaTasks.remove(exerciseId);
    }

    /**
     * Check whether an Athena task is running for the given exerciseId
     *
     * @param exerciseId the exerciseId to check for a running Athena task
     * @return true, if a task for the given exerciseId is running
     */
    public boolean isTaskRunning(Long exerciseId) {
        return runningAthenaTasks.contains(exerciseId);
    }

    /**
     * Calls the remote Athena service to submit a Job for calculating automatic feedback
     *
     * @param exercise the exercise the automatic assessments should be calculated for
     */
    public void submitJob(TextExercise exercise) {
        submitJob(exercise, 1);
    }

    /**
     * Calls the remote Athena service to submit a Job for calculating automatic feedback
     * Falls back to naive splitting for less than 10 submissions
     * Note: See `TextSubmissionService:getTextSubmissionsByExerciseId` for selection of Submissions.
     *
     * @param exercise   the exercise the automatic assessments should be calculated for
     * @param maxRetries number of retries before the request will be canceled
     */
    public void submitJob(TextExercise exercise, int maxRetries) {
        log.debug("Start Athena Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        // Find all submissions for Exercise
        // We only support english languages so far, to prevent corruption of the clustering
        // TODO: change
        List<TextSubmission> textSubmissions = textSubmissionRepository.getTextSubmissionsWithTextBlocksByExerciseIdAndLanguage(exercise.getId(), Language.ENGLISH);

        // Athena only works with 10 or more submissions
        if (textSubmissions.size() < 10) {
            return;
        }

        log.info("Calling Remote Service to calculate automatic feedback for {} submissions.", textSubmissions.size());

        try {
            final RequestDTO request = new RequestDTO(exercise.getId(), textSubmissions);
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/submit", request, maxRetries);
            log.info("Remote Service to calculate automatic feedback responded: {}", response.detail);

            // Register task for exercise as running, AthenaResource calls finishTask on result receive
            startTask(exercise.getId());
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
        }
    }

}
