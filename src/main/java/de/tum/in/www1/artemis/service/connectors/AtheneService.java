package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.service.TextBlockService;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.*;

import static de.tum.in.www1.artemis.config.Constants.ATHENE_RESULT_API_PATH;
import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authorizationHeaderForSymmetricSecret;
import static java.util.stream.Collectors.toList;

@Service
@Profile("athene")
public class AtheneService {

    private final Logger log = LoggerFactory.getLogger(AtheneService.class);

    @Value("${server.url}")
    protected String ARTEMIS_SERVER_URL;

    @Value("${artemis.athene.submit-url}")
    private String API_ENDPOINT;

    @Value("${artemis.athene.base64-secret}")
    private String API_SECRET;

    private final TextSubmissionService textSubmissionService;

    private final TextBlockRepository textBlockRepository;

    private final TextBlockService textBlockService;

    private final RemoteArtemisServiceConnector<Request, Response> connector = new RemoteArtemisServiceConnector<>(log, Response.class);

    // Contains tasks submitted to Athene and currently processing
    private final List<Long> runningAtheneTasks = new ArrayList<>();

    public AtheneService(TextSubmissionService textSubmissionService, TextBlockRepository textBlockRepository, TextBlockService textBlockService) {
        this.textSubmissionService = textSubmissionService;
        this.textBlockRepository = textBlockRepository;
        this.textBlockService = textBlockService;
    }

    // region Request/Response DTOs
    private static class Request {

        public long courseId;
        public String callbackUrl;
        public List<TextSubmission> submissions;

        Request(@NotNull long courseId, @NotNull List<TextSubmission> submissions, @NotNull String callbackUrl) {
            this.courseId = courseId;
            this.callbackUrl = callbackUrl;
            this.submissions = submissionDTOs(submissions);
        }

        /**
         * Create new TextSubmission as DTO.
         */
        @NotNull
        private List<TextSubmission> submissionDTOs(@NotNull List<TextSubmission> submissions) {
            return submissions.stream().map(textSubmission -> {
                final TextSubmission submission = new TextSubmission();
                submission.setText(textSubmission.getText());
                submission.setId(textSubmission.getId());
                return submission;
            }).collect(toList());
        }
    }

    private static class Response {

        public String detail;

    }
    // endregion

    /**
     * Register an Athene task for an exercise as running
     * @param exerciseId the exerciseId which the Athene task is running for
     */
    public void startTask(Long exerciseId) {
        runningAtheneTasks.add(exerciseId);
    }

    /**
     * Delete an Athene task for an exercise from the running tasks
     * @param exerciseId the exerciseId which the Athene task finished for
     */
    public void finishTask(Long exerciseId) {
        runningAtheneTasks.remove(exerciseId);
    }

    /**
     * Check whether an Athene task is running for the given exerciseId
     * @param exerciseId the exerciseId to check for a running Athene task
     * @return true, if a task for the given exerciseId is running
     */
    public boolean isTaskRunning(Long exerciseId) {
        return runningAtheneTasks.contains(exerciseId);
    }

    /**
     * Calls the remote Athene service to submit a Job for calculating automatic feedback
     * @param exercise the exercise the automatic assessments should be calculated for
     */
    public void submitJob(TextExercise exercise) {
        submitJob(exercise, 1);
    }

    /**
     * Calls the remote Athene service to submit a Job for calculating automatic feedback
     * Falls back to naive splitting for less than 10 submissions
     * Note: See `TextSubmissionService:getTextSubmissionsByExerciseId` for selection of Submissions.
     * @param exercise the exercise the automatic assessments should be calculated for
     * @param maxRetries number of retries before the request will be canceled
     */
    public void submitJob(TextExercise exercise, int maxRetries) {
        log.debug("Start Athene Service for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ").");

        // Find all submissions for Exercise
        List<TextSubmission> textSubmissions = textSubmissionService.getTextSubmissionsByExerciseId(exercise.getId(), true, false);

        // We only support english languages so far, to prevent corruption of the clustering
        textSubmissions.removeIf(textSubmission -> textSubmission.getLanguage() != Language.ENGLISH);

        // Athene only works if more than 10 submissions are available
        // else textBlockService is used
        if (textSubmissions.size() >= 10) {

            log.info("Calling Remote Service to calculate automatic feedback for " + textSubmissions.size() + " submissions.");

            try {
                final Request request = new Request(exercise.getId(), textSubmissions, ARTEMIS_SERVER_URL + ATHENE_RESULT_API_PATH + exercise.getId());
                Response response = connector.invokeWithRetry(API_ENDPOINT, request, authorizationHeaderForSymmetricSecret(API_SECRET), maxRetries);
                log.info("Remote Service to calculate automatic feedback responded: " + response.detail);

                // Register task for exercise as running, AtheneResource calls finishTask on result receive
                startTask(exercise.getId());
            } catch (NetworkingError networkingError) {
                networkingError.printStackTrace();
            }

        } else {

            log.info("More than 10 submissions needed to calculate automatic feedback. Falling back to naive splitting");

            List<TextBlock> set = new ArrayList<>();

            // Split Submissions into Blocks
            for (TextSubmission textSubmission : textSubmissions) {

                final List<TextBlock> blocks = textBlockService.splitSubmissionIntoBlocks(textSubmission);
                textSubmission.setBlocks(blocks);
                set.addAll(blocks);

            }

            textBlockRepository.saveAll(set);

        }
    }

}
