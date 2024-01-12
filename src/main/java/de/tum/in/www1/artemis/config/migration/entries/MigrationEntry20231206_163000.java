package de.tum.in.www1.artemis.config.migration.entries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

/**
 * This migration entry migrates all Haskell and OCaml exercises that need to check out the solution repository
 * in a student build plan. This is necessary because earlier versions of Artemis did not store this information.
 */
@Component
public class MigrationEntry20231206_163000 extends MigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int THREADS = 10;

    private static final List<String> MIGRATABLE_PROFILES = List.of("bamboo");

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20231206_163000.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final Optional<BambooMigrationService> ciMigrationService;

    private final Environment environment;

    public MigrationEntry20231206_163000(ProgrammingExerciseRepository programmingExerciseRepository, Optional<BambooMigrationService> ciMigrationService,
            Environment environment) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.ciMigrationService = ciMigrationService;
        this.environment = environment;
    }

    @Override
    public void execute() {
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        if (activeProfiles.stream().noneMatch(MIGRATABLE_PROFILES::contains) || ciMigrationService.isEmpty()) {
            log.info("Migration will be skipped and marked run because the system does not support a tech-stack that requires this migration: {}", activeProfiles);
            return;
        }

        var exercisesToMigrate = programmingExerciseRepository.findAllByProgrammingLanguageWithTemplateParticipation(ProgrammingLanguage.HASKELL);
        exercisesToMigrate.addAll(programmingExerciseRepository.findAllByProgrammingLanguageWithTemplateParticipation(ProgrammingLanguage.OCAML));

        if (exercisesToMigrate.isEmpty()) {
            // no exercises to change, migration complete
            return;
        }

        log.info("Migrating {} programming exercises. This might take a while.", exercisesToMigrate.size());

        ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
        List<CompletableFuture<?>> allFutures = new ArrayList<>();

        for (ProgrammingExercise exercise : exercisesToMigrate) {
            allFutures.add(CompletableFuture.runAsync(() -> {
                boolean checkoutSolutionRepository = false;
                try {
                    checkoutSolutionRepository = ciMigrationService.get().hasSolutionRepository(exercise.getTemplateBuildPlanId());
                }
                catch (Exception e) {
                    log.error("Error while checking if exercise {} needs to check out solution repository in build plan, setting checkoutSolutionRepository to false",
                            exercise.getId(), e);
                }
                exercise.setCheckoutSolutionRepository(checkoutSolutionRepository);
                log.debug("Migrated exercise {}", exercise.getId());
                programmingExerciseRepository.save(exercise);
            }, executorService));
        }

        // Wait until all currently loaded exercises are migrated to avoid loading too many exercises at the same time.
        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[] {})).join();

        executorService.shutdown();
    }

    @Override
    public String author() {
        return "reschandreas";
    }

    @Override
    public String date() {
        return "20231206_163000";
    }
}
