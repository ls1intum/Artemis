package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;
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
import de.tum.in.www1.artemis.service.connectors.TextEmbeddingService;
import de.tum.in.www1.artemis.service.connectors.TextSimilarityClusteringService;

@Service
@Profile("automaticText")
public class TextClusteringService {

    private final Logger log = LoggerFactory.getLogger(TextClusteringService.class);

    private final TextBlockService textBlockService;

    private final TextSubmissionService textSubmissionService;

    private final TextSimilarityClusteringService textSimilarityClusteringService;

    private final TextClusterRepository textClusterRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextEmbeddingService textEmbeddingService;

    private final TextAssessmentQueueService textAssessmentQueueService;

    @Value("${artemis.automatic-text.embedding-chunk-size}")
    private int embeddingChunkSize;

    public TextClusteringService(TextBlockService textBlockService, TextSubmissionService textSubmissionService, TextClusterRepository textClusterRepository,
            TextBlockRepository textBlockRepository, TextSimilarityClusteringService textSimilarityClusteringService, TextEmbeddingService textEmbeddingService,
            TextAssessmentQueueService textAssessmentQueueService) {
        this.textBlockService = textBlockService;
        this.textSubmissionService = textSubmissionService;
        this.textClusterRepository = textClusterRepository;
        this.textBlockRepository = textBlockRepository;
        this.textSimilarityClusteringService = textSimilarityClusteringService;
        this.textEmbeddingService = textEmbeddingService;
        this.textAssessmentQueueService = textAssessmentQueueService;
    }

    private List<TextEmbedding> computeEmbeddings(List<TextBlock> blocks) {
        final AtomicInteger counter = new AtomicInteger();

        Map<Integer, List<TextBlock>> chunks = blocks.stream().collect(groupingBy(block -> counter.getAndIncrement() / embeddingChunkSize, toList()));
        log.debug("Splitted Text Blocks into " + chunks.size() + " chunks.");

        // Initialize Result Array with final size to prevent overhead of ArrayLists internal array resizing.
        final List<TextEmbedding> textEmbeddings = new ArrayList<>(blocks.size());
        chunks.forEach((i, chunk) -> {
            log.debug("Computing Language Embeddigns for Chunk " + i + " / " + chunks.size() + ".");
            try {
                textEmbeddings.addAll(textEmbeddingService.embedTextBlocks(chunk, 2));
            }
            catch (NetworkingError networkingError) {
                networkingError.printStackTrace();
            }
        });

        return textEmbeddings;
    }

    @Transactional
    public void calculateClusters(TextExercise exercise) {
        long start = System.currentTimeMillis();
        log.debug("Start Clustering for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ").");

        // Find all submissions for Exercise and Split them into Blocks
        Map<String, TextBlock> textBlockMap = textBlockRepository.saveAll(getTextBlocks(exercise.getId())).stream().limit(100).collect(toMap(TextBlock::getId, block -> block));
        List<TextEmbedding> embeddings = computeEmbeddings(new ArrayList<>(textBlockMap.values()));

        // Invoke clustering for Text Blocks
        final Map<Integer, TextCluster> clusters;
        try {
            clusters = textSimilarityClusteringService.clusterTextBlocks(embeddings, 3);
        }
        catch (NetworkingError networkingError) {
            networkingError.printStackTrace();
            return;
        }

        // Remove Cluster with Key "-1" as it is only contains the blocks belonging to no cluster.
        clusters.remove(-1);
        final List<TextCluster> savedClusters = textClusterRepository.saveAll(clusters.values());

        for (TextCluster cluster : savedClusters) {
            cluster.setExercise(exercise);
            List<TextBlock> updatedBlockReferences = cluster.getBlocks().parallelStream().map(block -> textBlockMap.get(block.getId())).peek(block -> block.setCluster(cluster))
                    .collect(toList());

            textAssessmentQueueService.setAddedDistances(updatedBlockReferences, cluster);

            updatedBlockReferences = textBlockRepository.saveAll(updatedBlockReferences);
            cluster.setBlocks(updatedBlockReferences);
        }

        // Store Clusters in Database
        textClusterRepository.saveAll(savedClusters);

        log.info("Found " + clusters.size() + " clusters for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") in " + (System.currentTimeMillis() - start)
                + "ms");
    }

    /**
     * Fetch all submissions for an exercise and split them up into TextBlocks.
     * Note: See `TextSubmissionService:getTextSubmissionsByExerciseId` for selection of Submissions.
     *
     * @param exerciseId id of relevant TextExercise
     * @return List of TextBlocks from *all* submissions for the specified TextExercise.
     */
    @NotNull
    @Transactional(readOnly = true)
    List<TextBlock> getTextBlocks(Long exerciseId) {
        List<TextBlock> set = new ArrayList<>();
        for (TextSubmission textSubmission : textSubmissionService.getTextSubmissionsByExerciseId(exerciseId, true)) {
            if (textSubmission.getLanguage() != Language.ENGLISH) {
                // We only support english languages so far, to prevent corruption of the clustering
                continue;
            }
            final List<TextBlock> blocks = textBlockService.splitSubmissionIntoBlocks(textSubmission);
            textSubmission.setBlocks(blocks);
            set.addAll(blocks);
        }
        return set;
    }

}
