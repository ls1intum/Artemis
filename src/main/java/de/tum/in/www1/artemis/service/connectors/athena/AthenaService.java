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
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.TextAssessmentQueueService;

@Service
@Profile("athena")
public class AthenaService {

    private final Logger log = LoggerFactory.getLogger(AthenaService.class);

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

        // use Maps to much more easily change the transfer format to better fit the Athena API
        public Map<String, Object> exercise;

        public List<Map<String, Object>> submissions;

        RequestDTO(@NotNull TextExercise exercise, @NotNull List<TextSubmission> submissions) {
            this.exercise = createExerciseDTO(exercise);
            this.submissions = submissions.stream().map(submission -> createSubmissionDTO(submission, exercise.getId())).toList();
        }

        /**
         * Converts a TextExercise to a DTO object to prepare for sending it to Athena in a REST call.
         */
        @NotNull
        private static Map<String, Object> createExerciseDTO(@NotNull TextExercise exercise) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", exercise.getId());
            map.put("title", exercise.getTitle());
            map.put("type", "text");
            map.put("maxPoints", exercise.getMaxPoints());
            map.put("bonusPoints", exercise.getBonusPoints());
            map.put("gradingInstructions", exercise.getGradingInstructions());
            map.put("problemStatement", exercise.getProblemStatement());
            return map;
        }

        /**
         * Converts TextSubmissions to DTO objects to prepare for sending them to Athena in a REST call.
         */
        @NotNull
        private static Map<String, Object> createSubmissionDTO(@NotNull TextSubmission submission, @NotNull long exerciseId) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", submission.getId());
            map.put("exerciseId", exerciseId);
            map.put("text", submission.getText());
            map.put("language", submission.getLanguage());
            return map;
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
    public void sendSubmissions(TextExercise exercise) {
        sendSubmissions(exercise, 1);
    }

    /**
     * Calls the remote Athena service to submit a Job for calculating automatic feedback
     * Note: See `TextSubmissionService:getTextSubmissionsByExerciseId` for selection of Submissions.
     *
     * @param exercise   the exercise the automatic assessments should be calculated for
     * @param maxRetries number of retries before the request will be canceled
     */
    public void sendSubmissions(TextExercise exercise, int maxRetries) {
        log.debug("Start Athena Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        // Find all text submissions for exercise (later we will support others)
        List<TextSubmission> textSubmissions = textSubmissionRepository.getTextSubmissionsWithTextBlocksByExerciseId(exercise.getId());

        log.info("Calling Remote Service to calculate automatic feedback for {} submissions.", textSubmissions.size());

        try {
            final RequestDTO request = new RequestDTO(exercise, textSubmissions);
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/submissions", request, maxRetries);
            log.info("Remote Service to calculate automatic feedback responded: {}", response.detail);

            // Register submission processing task for exercise as running
            startTask(exercise.getId());
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
        }
    }

}
