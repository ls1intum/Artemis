package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
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

    public TextClusteringService(TextBlockService textBlockService, TextSubmissionService textSubmissionService, TextClusterRepository textClusterRepository,
            TextBlockRepository textBlockRepository, TextSimilarityClusteringService textSimilarityClusteringService) {
        this.textBlockService = textBlockService;
        this.textSubmissionService = textSubmissionService;
        this.textClusterRepository = textClusterRepository;
        this.textBlockRepository = textBlockRepository;
        this.textSimilarityClusteringService = textSimilarityClusteringService;
    }

    public void calculateClusters(TextExercise exercise) {
        long start = System.currentTimeMillis();
        log.debug("Start Clustering for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ").");

        // Find all submissions for Exercise and Split them into Blocks
        final List<TextBlock> blocks = getTextBlocks(exercise.getId());// .stream().limit(100).collect(Collectors.toList());
        final List<TextBlock> savedBlocks = textBlockRepository.saveAll(blocks);

        // Invoke clustering for Text Blocks
        final Map<Integer, TextCluster> clusters;
        try {
            clusters = textSimilarityClusteringService.clusterTextBlocks(savedBlocks, 3);
        }
        catch (TextSimilarityClusteringService.NetworkingError networkingError) {
            networkingError.printStackTrace();
            return;
        }

        // Remove Cluster with Key "-1" as it is only contains the blocks belonging to no cluster.
        clusters.remove(-1);
        final List<TextCluster> savedClusters = textClusterRepository.saveAll(clusters.values());

        for (TextCluster cluster : savedClusters) {
            cluster.setExercise(exercise);
            final List<TextBlock> updatedBlockReferences = cluster.getBlocks().parallelStream().map(block -> savedBlocks.stream()
                    .filter(element -> element.getId().equals(block.getId())).findFirst().orElseThrow(() -> new IllegalStateException("Cannot handle unknown Text Block.")))
                    .peek(block -> block.setCluster(cluster)).collect(toList());
            cluster.setBlocks(updatedBlockReferences);
            // cluster.storeBlockOrder();
            textBlockRepository.saveAll(updatedBlockReferences);
        }

        // Store Clusters in Database
        textClusterRepository.saveAll(savedClusters);

        // TODO (Gregor): Sort Manual Assessment Queue
        // Pass clusters.values() ?

        log.info("Found " + clusters.size() + " clusters for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") in " + (System.currentTimeMillis() - start)
                + "ms");
    }

    @NotNull
    @Transactional(readOnly = true)
    List<TextBlock> getTextBlocks(Long exerciseId) {
        List<TextBlock> set = new ArrayList<>();
        for (TextSubmission textSubmission : textSubmissionService.getTextSubmissionsByExerciseId(exerciseId, true)) {
            final List<TextBlock> blocks = textBlockService.splitSubmissionIntoBlocks(textSubmission);
            textSubmission.setBlocks(blocks);
            set.addAll(blocks);
        }
        return set;
    }

}
