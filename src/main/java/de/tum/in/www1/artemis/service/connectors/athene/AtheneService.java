package de.tum.in.www1.artemis.service.connectors.athene;

import static de.tum.in.www1.artemis.config.Constants.ATHENE_RESULT_API_PATH;

import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.ase.athene.protobuf.Cluster;
import de.tum.in.ase.athene.protobuf.DistanceMatrixEntry;
import de.tum.in.ase.athene.protobuf.Segment;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.TextAssessmentQueueService;

@Service
@Profile("athene")
public class AtheneService {

    private final Logger log = LoggerFactory.getLogger(AtheneService.class);

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.athene.url}")
    private String atheneUrl;

    private final TextAssessmentQueueService textAssessmentQueueService;

    private final TextBlockRepository textBlockRepository;

    private final TextClusterRepository textClusterRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final AtheneConnector<RequestDTO, ResponseDTO> connector;

    // Contains tasks submitted to Athene and currently processing
    private final List<Long> runningAtheneTasks = new ArrayList<>();

    public AtheneService(TextSubmissionRepository textSubmissionRepository, TextBlockRepository textBlockRepository, TextClusterRepository textClusterRepository,
            TextExerciseRepository textExerciseRepository, TextAssessmentQueueService textAssessmentQueueService,
            @Qualifier("atheneRestTemplate") RestTemplate atheneRestTemplate) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.textBlockRepository = textBlockRepository;
        this.textClusterRepository = textClusterRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.textAssessmentQueueService = textAssessmentQueueService;
        connector = new AtheneConnector<>(log, atheneRestTemplate, ResponseDTO.class);
    }

    // region Request/Response DTOs
    private static class RequestDTO {

        public long courseId;

        public String callbackUrl;

        public List<TextSubmission> submissions;

        RequestDTO(@NotNull long courseId, @NotNull List<TextSubmission> submissions, @NotNull String callbackUrl) {
            this.courseId = courseId;
            this.callbackUrl = callbackUrl;
            this.submissions = createSubmissionDTOs(submissions);
        }

        /**
         * Converts TextSubmissions to DTO objects to prepare for sending them to Athene in a REST call.
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
     * Register an Athene task for an exercise as running
     *
     * @param exerciseId the exerciseId which the Athene task is running for
     */
    public void startTask(Long exerciseId) {
        runningAtheneTasks.add(exerciseId);
    }

    /**
     * Delete an Athene task for an exercise from the running tasks
     *
     * @param exerciseId the exerciseId which the Athene task finished for
     */
    public void finishTask(Long exerciseId) {
        runningAtheneTasks.remove(exerciseId);
    }

    /**
     * Check whether an Athene task is running for the given exerciseId
     *
     * @param exerciseId the exerciseId to check for a running Athene task
     * @return true, if a task for the given exerciseId is running
     */
    public boolean isTaskRunning(Long exerciseId) {
        return runningAtheneTasks.contains(exerciseId);
    }

    /**
     * Calls the remote Athene service to submit a Job for calculating automatic feedback
     *
     * @param exercise the exercise the automatic assessments should be calculated for
     */
    public void submitJob(TextExercise exercise) {
        submitJob(exercise, 1);
    }

    /**
     * Calls the remote Athene service to submit a Job for calculating automatic feedback
     * Falls back to naive splitting for less than 10 submissions
     * Note: See `TextSubmissionService:getTextSubmissionsByExerciseId` for selection of Submissions.
     *
     * @param exercise   the exercise the automatic assessments should be calculated for
     * @param maxRetries number of retries before the request will be canceled
     */
    public void submitJob(TextExercise exercise, int maxRetries) {
        log.debug("Start Athene Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        // Find all submissions for Exercise
        // We only support english languages so far, to prevent corruption of the clustering
        List<TextSubmission> textSubmissions = textSubmissionRepository.getTextSubmissionsWithTextBlocksByExerciseIdAndLanguage(exercise.getId(), Language.ENGLISH);

        // Athene only works with 10 or more submissions
        if (textSubmissions.size() < 10) {
            return;
        }

        log.info("Calling Remote Service to calculate automatic feedback for {} submissions.", textSubmissions.size());

        try {
            final RequestDTO request = new RequestDTO(exercise.getId(), textSubmissions, artemisServerUrl + ATHENE_RESULT_API_PATH + exercise.getId());
            ResponseDTO response = connector.invokeWithRetry(atheneUrl + "/submit", request, maxRetries);
            log.info("Remote Service to calculate automatic feedback responded: {}", response.detail);

            // Register task for exercise as running, AtheneResource calls finishTask on result receive
            startTask(exercise.getId());
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
        }
    }

    /**
     * Processes results coming back from the Athene system via callbackUrl (see AtheneResource)
     *
     * @param clusters   the list of calculated clusters to save to the database
     * @param segments   the list of calculated textBlocks to save to the database
     * @param exerciseId the exercise the automatic feedback suggestions were calculated for
     */
    public void processResult(List<Cluster> clusters, List<Segment> segments, Long exerciseId) {
        log.debug("Start processing incoming Athene results for exercise with id {}", exerciseId);

        // Parse textBlocks (blocks will come as protobuf Segment with their submissionId and need to be parsed)
        List<TextBlock> textBlocks = parseTextBlocks(segments, exerciseId);
        // Parse textClusters (clusters will come as protobuf Cluster and need to be parsed)
        List<TextCluster> textClusters = parseTextClusters(clusters);

        // Save textBlocks in Database
        final Map<String, TextBlock> textBlockMap = textBlockRepository.saveAll(textBlocks).stream().collect(Collectors.toMap(TextBlock::getId, block -> block));

        // Save clusters in Database
        processClusters(textClusters, textBlockMap, exerciseId);

        // Notify atheneService of finished task
        finishTask(exerciseId);

        log.debug("Finished processing incoming Athene results for exercise with id {}", exerciseId);
    }

    /**
     * Parse text blocks of type Athene-Protobuf-Segment to TextBlock linked to their submission
     *
     * @param segments   the list of text blocks of type Athene-Protobuf-Segment to parse
     * @param exerciseId the exerciseId of the exercise the blocks belong to
     * @return list of TextBlocks
     */
    public List<TextBlock> parseTextBlocks(List<Segment> segments, Long exerciseId) {
        // Create submissionsMap for lookup
        List<TextSubmission> submissions = textSubmissionRepository.getTextSubmissionsWithTextBlocksByExerciseId(exerciseId);
        Map<Long, TextSubmission> submissionsMap = submissions.stream().collect(Collectors.toMap(/* Key: */ Submission::getId, /* Value: */ submission -> submission));

        // Get knowledge of exercise
        TextAssessmentKnowledge textAssessmentKnowledge = textExerciseRepository.findById(exerciseId).get().getKnowledge();
        // Map textBlocks to submissions
        List<TextBlock> textBlocks = new ArrayList<>();
        for (Segment segment : segments) {
            // Convert Protobuf-TextBlock (including the submissionId) to TextBlock Entity
            TextBlock newBlock = new TextBlock();
            newBlock.setId(segment.getId());
            newBlock.setText(segment.getText());
            newBlock.setStartIndex(segment.getStartIndex());
            newBlock.setEndIndex(segment.getEndIndex());
            newBlock.automatic();

            // Set TextBlock knowledge
            newBlock.setKnowledge(textAssessmentKnowledge);

            // take the corresponding TextSubmission and add the text blocks.
            // The addBlocks method also sets the submission in the textBlock
            long submissionId = segment.getSubmissionId();
            var textSubmission = submissionsMap.get(submissionId);
            if (textSubmission == null) {
                continue;
            }
            textSubmission.addBlock(newBlock);
            textBlocks.add(newBlock);
        }

        return textBlocks;
    }

    /**
     * Parse text clusters of type Athene-Protobuf-Cluster to TextCluster
     *
     * @param clusters   the list of text clusters of type Athene-Protobuf-Cluster to parse
     * @return list of TextClusters
     */
    public List<TextCluster> parseTextClusters(List<Cluster> clusters) {
        List<TextCluster> textClusters = new ArrayList<>();
        for (Cluster cluster : clusters) {
            TextCluster textCluster = new TextCluster();
            List<TextBlock> blocks = cluster.getSegmentsList().stream().map(s -> new TextBlock().id(s.getId())).collect(Collectors.toCollection(ArrayList::new));
            textCluster.setBlocks(blocks);

            double[][] distanceMatrix = new double[blocks.size()][blocks.size()];
            for (DistanceMatrixEntry entry : cluster.getDistanceMatrixList()) {
                distanceMatrix[entry.getX()][entry.getY()] = entry.getValue();
            }
            textCluster.setDistanceMatrix(distanceMatrix);
            textClusters.add(textCluster);
        }
        return textClusters;
    }

    /**
     * Process clusters, link them with text blocks and vice versa, and save all in the database
     *
     * @param textClusters The list of textClusters to process
     * @param textBlockMap The map of textBlocks belonging to the clusters
     * @param exerciseId   The exerciseId of the exercise the blocks belong to
     */
    public void processClusters(List<TextCluster> textClusters, Map<String, TextBlock> textBlockMap, Long exerciseId) {

        final List<TextCluster> savedClusters = textClusterRepository.saveAll(textClusters);

        // Find exercise, which the clusters belong to
        Optional<TextExercise> optionalTextExercise = textExerciseRepository.findById(exerciseId);
        if (optionalTextExercise.isEmpty()) {
            log.error("Error while processing Athene clusters. Exercise with id {} not found", exerciseId);
            return;
        }
        TextExercise textExercise = optionalTextExercise.get();

        // Link clusters with blocks
        for (TextCluster cluster : savedClusters) {
            cluster.setExercise(textExercise);
            List<TextBlock> updatedBlockReferences = cluster.getBlocks().parallelStream().map(block -> textBlockMap.get(block.getId())).peek(block -> block.setCluster(cluster))
                    .collect(Collectors.toCollection(ArrayList::new));
            textAssessmentQueueService.setAddedDistances(updatedBlockReferences, cluster);
            cluster.setBlocks(updatedBlockReferences);
            textBlockRepository.saveAll(updatedBlockReferences);
        }

        // Save clusters in Database
        textClusterRepository.saveAll(savedClusters);
    }

}
