package de.tum.in.www1.artemis.config.migration.entries;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

/**
 * Migration for automatically updating all Programming exercise problem statements, removing their test names and replacing them by test ids.
 */
public class MigrationEntry20230810_150000 extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20230810_150000.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService taskService;

    private static final int BATCH_SIZE = 100;

    private static final int THREADS = 10;

    public MigrationEntry20230810_150000(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService taskService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.taskService = taskService;
    }

    @Override
    public void execute() {
        long size = programmingExerciseRepository.count();
        if (size == 0) {
            // no exercises to change, migration complete
            return;
        }

        log.info("Migrating all {} programming exercises. This might take a while.", size);

        ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
        Pageable pageable = Pageable.ofSize(BATCH_SIZE);

        while (true) {
            Page<ProgrammingExercise> exercisePage = programmingExerciseRepository.findAll(pageable);
            var exercisePartitions = Lists.partition(exercisePage.toList(), THREADS);
            CompletableFuture<?>[] allFutures = new CompletableFuture[Math.min(THREADS, exercisePartitions.size())];

            for (int i = 0; i < exercisePartitions.size(); i++) {
                var partition = exercisePartitions.get(i);
                allFutures[i] = CompletableFuture.runAsync(() -> {
                    SecurityUtils.setAuthorizationObject();
                    for (var exercise : partition) {
                        log.debug("Migrating exercise {}", exercise.getId());
                        taskService.replaceTestNamesWithIds(exercise);
                    }
                    programmingExerciseRepository.saveAll(partition);
                }, executorService);
            }

            // Wait until all currently loaded exercises are migrated to avoid loading too many exercises at the same time.
            CompletableFuture.allOf(allFutures).join();

            if (exercisePage.isLast()) {
                break;
            }
            pageable = pageable.next();
        }

        executorService.shutdown();
    }

    @Override
    public String author() {
        return "welscher";
    }

    @Override
    public String date() {
        return "20230810_150000";
    }
}
