package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.service.connectors.TextSimilarityClusteringService.Cluster;
import static de.tum.in.www1.artemis.service.connectors.TextSimilarityClusteringService.TextBlock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
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

    TextClusteringScheduleService(ExerciseLifecycleService exerciseLifecycleService, TextSimilarityClusteringService textSimilarityClusteringService,
            TextExerciseService textExerciseService, TextBlockService textBlockService, TextSubmissionService textSubmissionService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
        this.textSimilarityClusteringService = textSimilarityClusteringService;
        this.textExerciseService = textExerciseService;
        this.textBlockService = textBlockService;
        this.textSubmissionService = textSubmissionService;
    }

    @PostConstruct
    private void scheduleRunningExercisesOnStartup() {
        List<TextExercise> runningTextExercises = textExerciseService.findAllWithFutureDueDate();
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
            final List<TextBlock> blocks = getTextBlocks(exercise.getId());

            // Invoke clustering for Text Blocks
            final Map<Integer, Cluster> clusters;
            try {
                clusters = textSimilarityClusteringService.clusterTextBlocks(blocks, 3);
            }
            catch (TextSimilarityClusteringService.NetworkingError networkingError) {
                networkingError.printStackTrace();
                return;
            }

            // TODO: Persist Blocks
            clusters.size();

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
    private List<TextBlock> getTextBlocks(Long exerciseId) {
        return textSubmissionService.getTextSubmissionsByExerciseId(exerciseId, true).stream().map(TextSubmission::getText).map(textBlockService::splitSubmissionIntoBlocks)
                .flatMap(List::stream).map(clause -> {
                    final TextBlock block = new TextBlock();
                    block.text = clause;
                    return block;
                }).collect(Collectors.toList());
    }

}
