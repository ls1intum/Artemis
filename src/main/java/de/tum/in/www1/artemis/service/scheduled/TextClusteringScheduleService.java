package de.tum.in.www1.artemis.service.scheduled;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.service.ExerciseLifecycleService;
import de.tum.in.www1.artemis.service.TextBlockService;
import de.tum.in.www1.artemis.service.TextExerciseService;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import de.tum.in.www1.artemis.service.connectors.TextSimilarityClusteringService;

@Service
@Profile("automaticText")
public class TextClusteringScheduleService {

    private final Logger log = LoggerFactory.getLogger(TextClusteringScheduleService.class);

    private final ExerciseLifecycleService exerciseLifecycleService;

    private final TextSimilarityClusteringService textSimilarityClusteringService;

    private final TextExerciseService textExerciseService;

    private final TextBlockService textBlockService;

    private final TextSubmissionService textSubmissionService;

    private final Map<Long, ScheduledFuture> scheduledClusteringTasks = new HashMap<>();

    private final TextClusterRepository textClusterRepository;

    private final TextBlockRepository textBlockRepository;

    TextClusteringScheduleService(ExerciseLifecycleService exerciseLifecycleService, TextSimilarityClusteringService textSimilarityClusteringService,
            TextExerciseService textExerciseService, TextBlockService textBlockService, TextSubmissionService textSubmissionService, TextClusterRepository textClusterRepository,
            TextBlockRepository textBlockRepository) {
        this.exerciseLifecycleService = exerciseLifecycleService;
        this.textSimilarityClusteringService = textSimilarityClusteringService;
        this.textExerciseService = textExerciseService;
        this.textBlockService = textBlockService;
        this.textSubmissionService = textSubmissionService;
        this.textClusterRepository = textClusterRepository;
        this.textBlockRepository = textBlockRepository;
    }

    @PostConstruct
    private void scheduleRunningExercisesOnStartup() {
        List<TextExercise> runningTextExercises = textExerciseService.findAllAutomaticAssessmentTextExercisesWithFutureDueDate();
        runningTextExercises.forEach(this::scheduleExerciseForClustering);
        log.info("Scheduled text clustering for " + runningTextExercises.size() + " text exercises with future due dates.");
    }

    public void scheduleExerciseForClustering(TextExercise exercise) {
        // check if already scheduled for exercise. if so, cancel
        cancelScheduledClustering(exercise);

        ScheduledFuture future = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, () -> {
            long start = System.currentTimeMillis();
            log.debug("Start Clustering for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ").");

            // Find all submissions for Exercise and Split them into Blocks
            final Set<TextBlock> blocks = getTextBlocks(exercise.getId());
            textBlockRepository.saveAll(blocks);

            // Invoke clustering for Text Blocks
            final Map<Integer, TextCluster> clusters;
            try {
                clusters = textSimilarityClusteringService.clusterTextBlocks(blocks, 3);
            }
            catch (TextSimilarityClusteringService.NetworkingError networkingError) {
                networkingError.printStackTrace();
                return;
            }

            // Remove Cluster with Key "-1" as it is only contains the blocks belonging to no cluster.
            clusters.remove(-1);

            final Stream<TextBlock> textBlockStream = blocks.stream();
            for (TextCluster cluster : clusters.values()) {
                cluster.setExercise(exercise);
                final List<TextBlock> updatedBlockReferences = cluster.getBlocks().parallelStream().map(block -> textBlockStream
                        .filter(element -> element.getId().equals(block.getId())).findFirst().orElseThrow(() -> new IllegalStateException("Cannot handle unknown Text Block.")))
                        .collect(Collectors.toList());
                cluster.setBlocks(updatedBlockReferences);
                cluster.storeBlockOrder();
            }

            // Store Clusters in Database
            textClusterRepository.saveAll(clusters.values());

            // TODO (Gregor): Sort Manual Assessment Queue
            // Pass clusters.values() ?

            log.info("Found " + clusters.size() + " clusters for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") in "
                    + (System.currentTimeMillis() - start) + "ms");
        });

        scheduledClusteringTasks.put(exercise.getId(), future);
        log.debug("Scheduled Clustering for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") for " + exercise.getDueDate() + ".");
    }

    public void cancelScheduledClustering(TextExercise exercise) {
        ScheduledFuture future = scheduledClusteringTasks.get(exercise.getId());
        if (future != null) {
            future.cancel(false);
            scheduledClusteringTasks.remove(exercise.getId());
        }
    }

    @NotNull
    private Set<TextBlock> getTextBlocks(Long exerciseId) {
        Set<TextBlock> set = new HashSet<>();
        for (TextSubmission textSubmission : textSubmissionService.getTextSubmissionsByExerciseId(exerciseId, true)) {
            String text = textSubmission.getText();
            final List<TextBlock> blocks = textBlockService.splitSubmissionIntoBlocks(text);
            blocks.forEach(block -> block.setSubmission(textSubmission));
            textSubmission.setBlocks(blocks);
            set.addAll(blocks);
        }
        return set;
    }

}
