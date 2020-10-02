package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.dto.AtheneDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * REST controller for managing Athene results.
 */
@RestController
@RequestMapping(Constants.ATHENE_RESULT_API_PATH)
@Profile("athene")
public class AtheneResource {

    @Value("${artemis.athene.base64-secret}")
    private String API_SECRET;

    private final Logger log = LoggerFactory.getLogger(AtheneResource.class);

    private final TextBlockRepository textBlockRepository;

    private final TextClusterRepository textClusterRepository;

    private final TextAssessmentQueueService textAssessmentQueueService;

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionService textSubmissionService;

    public AtheneResource(TextBlockRepository textBlockRepository, TextClusterRepository textClusterRepository, TextAssessmentQueueService textAssessmentQueueService,
                          TextExerciseRepository textExerciseRepository, TextSubmissionService textSubmissionService) {
        this.textBlockRepository = textBlockRepository;
        this.textClusterRepository = textClusterRepository;
        this.textAssessmentQueueService = textAssessmentQueueService;
        this.textExerciseRepository = textExerciseRepository;
        this.textSubmissionService = textSubmissionService;
    }

    /**
     * Parse text blocks of type AtheneDTO.TextBlock to TextBlocks linked to their submission
     *
     * @param blocks The list of AtheneDTO-blocks to parse
     * @param exerciseId The exerciseId of the exercise the blocks belong to
     * @return list of TextBlocks
     */
    private List<TextBlock> parseTextBlocks(List<AtheneDTO.TextBlock> blocks, Long exerciseId) {
        // Create submissionsMap for lookup
        List<TextSubmission> submissions = textSubmissionService.getTextSubmissionsByExerciseId(exerciseId, true, false);
        Map<Long, TextSubmission> submissionsMap = submissions.stream()
            .collect(toMap(/* Key: */ (submission -> submission.getId()), /* Value: */ (submission -> submission)));

        // Map textBlocks to submissions
        List<TextBlock> textBlocks = new LinkedList();
        for (AtheneDTO.TextBlock t: blocks) {
            TextBlock newBlock = new TextBlock();
            newBlock.setId(t.id);
            newBlock.setText(t.text);
            newBlock.setStartIndex(t.startIndex);
            newBlock.setEndIndex(t.endIndex);
            newBlock.automatic();

            // take the corresponding TextSubmission and add the text blocks.
            // The addBlocks method also sets the submission in the textBlock
            TextSubmission s = submissionsMap.get(t.submissionId);
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
    private void processClusters(Map<Integer, TextCluster> clusterMap, Map<String, TextBlock> textBlockMap, Long exerciseId) {
        // Remove Cluster with Key "-1" as it is only contains the blocks belonging to no cluster.
        clusterMap.remove(-1);
        final List<TextCluster> savedClusters = textClusterRepository.saveAll(clusterMap.values());

        // Find exercise, which the clusters belong to
        Optional<TextExercise> optionalTextExercise = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesById(exerciseId);
        if (optionalTextExercise.isEmpty()) {
            log.error("Error while processing Athene clusters. Exercise with id " + exerciseId + "not found");
            new Error().printStackTrace();
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

        textClusterRepository.saveAll(savedClusters);
    }

    /**
     * Saves automatic textAssessments of Athene
     *
     * @param exerciseId The exerciseId of the exercise which will be saved
     * @param requestBody The calculation results containing blocks and clusters
     * @param auth The secret for authorization
     * @return 200 Ok if successful or 401 unauthorized if secret is wrong
     */
    @PostMapping(value = "/{exerciseId}", consumes = APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Result> testMethod(@PathVariable Long exerciseId, @RequestBody AtheneDTO requestBody, @RequestHeader("Authorization") String auth) {
        log.debug("REST request to inform about new Athene results for exercise: {}", exerciseId);

        // Check Authorization header
        if (!auth.equals(API_SECRET)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Parse requestBody
        Map<Integer, TextCluster> clusters = requestBody.clusters;
        List<TextBlock> textBlocks = parseTextBlocks(requestBody.blocks, exerciseId);

        //Save textBlocks in Database
        final Map<String, TextBlock> textBlockMap;
        textBlockMap = textBlockRepository.saveAll(textBlocks).stream().collect(toMap(TextBlock::getId, block -> block));

        //Save clusters in Database
        processClusters(clusters, textBlockMap, exerciseId);

        return ResponseEntity.status(HttpStatus.OK).build();

    }

}
