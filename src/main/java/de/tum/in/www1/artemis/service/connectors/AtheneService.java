package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.config.Constants.ATHENE_RESULT_API_PATH;
import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authorizationHeaderForSymmetricSecret;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.service.TextAssessmentQueueService;
import de.tum.in.www1.artemis.service.TextBlockService;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import de.tum.in.www1.artemis.web.rest.dto.AtheneDTO;

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

    private final TextAssessmentQueueService textAssessmentQueueService;

    private final TextBlockRepository textBlockRepository;

    private final TextBlockService textBlockService;

    private final TextClusterRepository textClusterRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionService textSubmissionService;

    private final RemoteArtemisServiceConnector<Request, Response> connector = new RemoteArtemisServiceConnector<>(log, Response.class);

    // Contains tasks submitted to Athene and currently processing
    private final List<Long> runningAtheneTasks = new ArrayList<>();

    public AtheneService(TextSubmissionService textSubmissionService, TextBlockRepository textBlockRepository, TextBlockService textBlockService,
            TextClusterRepository textClusterRepository, TextExerciseRepository textExerciseRepository, TextAssessmentQueueService textAssessmentQueueService) {
        this.textSubmissionService = textSubmissionService;
        this.textBlockRepository = textBlockRepository;
        this.textBlockService = textBlockService;
        this.textClusterRepository = textClusterRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.textAssessmentQueueService = textAssessmentQueueService;
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
        private static List<TextSubmission> submissionDTOs(@NotNull List<TextSubmission> submissions) {
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
        // We only support english languages so far, to prevent corruption of the clustering
        List<TextSubmission> textSubmissions = textSubmissionService.getTextSubmissionsWithTextBlocksByExerciseIdAndLanguage(exercise.getId(), Language.ENGLISH);

        // Athene only works with 10 or more submissions
        if (textSubmissions.size() < 10) {
            return;
        }

        log.info("Calling Remote Service to calculate automatic feedback for " + textSubmissions.size() + " submissions.");

        try {
            final Request request = new Request(exercise.getId(), textSubmissions, ARTEMIS_SERVER_URL + ATHENE_RESULT_API_PATH + exercise.getId());
            Response response = connector.invokeWithRetry(API_ENDPOINT, request, authorizationHeaderForSymmetricSecret(API_SECRET), maxRetries);
            log.info("Remote Service to calculate automatic feedback responded: " + response.detail);

            // Register task for exercise as running, AtheneResource calls finishTask on result receive
            startTask(exercise.getId());
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service", networkingError);
        }
    }

    /**
     * Processes results coming back from the Athene system via callbackUrl (see AtheneResource)
     * @param clusters the Map of calculated clusters to save to the database
     * @param blocks the list of calculated textBlocks to save to the database
     * @param exerciseId the exercise the automatic feedback suggestions were calculated for
     */
    public void processResult(Map<Integer, TextCluster> clusters, List<AtheneDTO.TextBlock> blocks, Long exerciseId) {
        log.info("Start processing incoming Athene results");

        // Parse textBlocks (blocks will come as AtheneDTO.TextBlock with their submissionId and need to be parsed)
        List<TextBlock> textBlocks = parseTextBlocks(blocks, exerciseId);

        // Save textBlocks in Database
        final Map<String, TextBlock> textBlockMap;
        textBlockMap = textBlockRepository.saveAll(textBlocks).stream().collect(toMap(TextBlock::getId, block -> block));

        // Save clusters in Database
        processClusters(clusters, textBlockMap, exerciseId);

        // Notify atheneService of finished task
        finishTask(exerciseId);

        log.info("Finished processing incoming Athene results");
    }

    /**
     * Parse text blocks of type AtheneDTO.TextBlock to TextBlocks linked to their submission
     *
     * @param blocks The list of AtheneDTO-blocks to parse
     * @param exerciseId The exerciseId of the exercise the blocks belong to
     * @return list of TextBlocks
     */
    public List<TextBlock> parseTextBlocks(List<AtheneDTO.TextBlock> blocks, Long exerciseId) {
        // Create submissionsMap for lookup
        List<TextSubmission> submissions = textSubmissionService.getTextSubmissionsWithTextBlocksByExerciseId(exerciseId);
        Map<Long, TextSubmission> submissionsMap = submissions.stream().collect(toMap(/* Key: */ Submission::getId, /* Value: */ submission -> submission));

        // Map textBlocks to submissions
        List<TextBlock> textBlocks = new LinkedList();
        for (AtheneDTO.TextBlock t : blocks) {
            // Convert DTO-TextBlock (including the submissionId) to TextBlock Entity
            TextBlock newBlock = new TextBlock();
            newBlock.setId(t.id);
            newBlock.setText(t.text);
            newBlock.setStartIndex(t.startIndex);
            newBlock.setEndIndex(t.endIndex);
            newBlock.automatic();

            // take the corresponding TextSubmission and add the text blocks.
            // The addBlocks method also sets the submission in the textBlock
            submissionsMap.get(t.submissionId).addBlock(newBlock);
            textBlocks.add(newBlock);
        }

        return textBlocks;
    }

    /**
     * Process clusters and save to database
     *
     * @param clusterMap The map of clusters to process
     * @param textBlockMap The map of textBlocks belonging to the clusters
     * @param exerciseId The exerciseId of the exercise the blocks belong to
     */
    public void processClusters(Map<Integer, TextCluster> clusterMap, Map<String, TextBlock> textBlockMap, Long exerciseId) {
        // Remove Cluster with Key "-1" as it is only contains the blocks belonging to no cluster.
        clusterMap.remove(-1);
        final List<TextCluster> savedClusters = textClusterRepository.saveAll(clusterMap.values());

        // Find exercise, which the clusters belong to
        Optional<TextExercise> optionalTextExercise = textExerciseRepository.findById(exerciseId);
        if (optionalTextExercise.isEmpty()) {
            log.error("Error while processing Athene clusters. Exercise with id " + exerciseId + " not found", new Error());
            return;
        }
        TextExercise textExercise = optionalTextExercise.get();

        // Link clusters with blocks
        for (TextCluster cluster : savedClusters) {
            cluster.setExercise(textExercise);
            List<TextBlock> updatedBlockReferences = cluster.getBlocks().parallelStream().map(block -> textBlockMap.get(block.getId())).peek(block -> block.setCluster(cluster))
                    .collect(toList());
            textAssessmentQueueService.setAddedDistances(updatedBlockReferences, cluster);
            updatedBlockReferences = textBlockRepository.saveAll(updatedBlockReferences);
            cluster.setBlocks(updatedBlockReferences);
        }

        // Save clusters in Database
        textClusterRepository.saveAll(savedClusters);
    }

}
