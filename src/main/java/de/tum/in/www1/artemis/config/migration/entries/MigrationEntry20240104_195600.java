package de.tum.in.www1.artemis.config.migration.entries;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.aeolus.Action;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusBuildScriptGenerationService;
import de.tum.in.www1.artemis.service.connectors.aeolus.ScriptAction;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;

@Component
public class MigrationEntry20240104_195600 extends ProgrammingExerciseMigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 32;

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within nine hours. Aborting migration.";

    private static final int TIMEOUT_IN_HOURS = 9;

    private static final List<String> MIGRATABLE_PROFILES = List.of("bamboo", "localci", "aeolus");

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20240104_195600.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final Optional<AeolusBuildScriptGenerationService> aeolusBuildScriptGenerationService;

    private final Environment environment;

    public MigrationEntry20240104_195600(ProgrammingExerciseRepository programmingExerciseRepository, Environment environment,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            Optional<AeolusBuildScriptGenerationService> aeolusBuildScriptGenerationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.environment = environment;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.aeolusBuildScriptGenerationService = aeolusBuildScriptGenerationService;
    }

    @Override
    public void execute() {
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        if (!new HashSet<>(activeProfiles).containsAll(MIGRATABLE_PROFILES)) {
            log.info("Migration will be skipped and marked run because the system does not support a tech-stack that requires this migration: {}",
                    List.of(environment.getActiveProfiles()));
            return;
        }

        var programmingExerciseCount = programmingExerciseRepository.count();

        if (programmingExerciseCount == 0) {
            // no exercises to change, migration complete
            return;
        }

        log.info("Will migrate {} programming exercises now. This might take a while", programmingExerciseCount);
        // Number of full batches. The last batch might be smaller
        long totalFullBatchCount = programmingExerciseCount / BATCH_SIZE;
        int threadCount = (int) Math.max(1, Math.min(totalFullBatchCount, MAX_THREAD_COUNT));

        // Use fixed thread pool to prevent loading too many exercises into memory at once
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        /*
         * migrate the solution participations, we don't care about template or
         */
        var solutionCount = solutionProgrammingExerciseParticipationRepository.count();
        AtomicInteger counter = new AtomicInteger(0);
        log.info("Found {} solution participations to migrate.", solutionCount);
        for (int currentPageStart = 0; currentPageStart < solutionCount; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            var solutionParticipationPage = solutionProgrammingExerciseParticipationRepository.findAllWithBuildPlanIdNotNull(pageable);
            log.info("Will migrate {} solution participations in batch.", solutionParticipationPage.getNumberOfElements());
            executorService.submit(() -> {
                migrateSolutions(solutionParticipationPage.toList());
                counter.addAndGet(solutionParticipationPage.getNumberOfElements());

            });
        }

        log.info("Submitted all solution participations to thread pool for migration.");

        shutdown(executorService, TIMEOUT_IN_HOURS, ERROR_MESSAGE);
        log.info("Finished migrating programming exercises and student participations");
        evaluateErrorList(programmingExerciseRepository);
    }

    /**
     * We only need to migrate the solution build plan, as the student build plans and template ones are not used in the new tech stack.
     *
     * @param solutionParticipations the solution participations to migrate
     */
    private void migrateSolutions(List<SolutionProgrammingExerciseParticipation> solutionParticipations) {
        if (aeolusBuildScriptGenerationService.isEmpty()) {
            log.error("Failed to migrate solutions because the AeolusBuildScriptGenerationService is not present.");
            return;
        }
        for (var solutionParticipation : solutionParticipations) {
            try {
                Windfile windfile = aeolusBuildScriptGenerationService.get().translateBuildPlan(solutionParticipation.getBuildPlanId());
                if (windfile == null) {
                    log.error("Failed to migrate solution participation with id {} because the windfile is null", solutionParticipation.getId());
                    errorList.add(solutionParticipation);
                    continue;
                }
                if (windfile.getMetadata().getDocker() == null) {
                    log.info(
                            "Migrating solution participation with id {} but the docker image is null, the build plan will be translated but not be functional as an instructor needs to add a docker image",
                            solutionParticipation.getId());
                }
                // we don't need the following fields in the windfile
                windfile.setRepositories(null);
                windfile.setId(null);
                windfile.setName(null);
                windfile.setAuthor(null);
                windfile.setGitCredentials(null);
                windfile.setDescription(null);
                this.makeWindfileLocalCIOptimized(windfile);
                // TODO: should we modify the docker parameters here? e.g. remove stuff like --net=host? -> this is more a question for after
                // the migration is done as localci does not respect the docker parameters anyway at the moment
                var programmingExercise = solutionParticipation.getProgrammingExercise();
                programmingExercise.setBuildPlanConfiguration(new Gson().toJson(windfile));
                programmingExerciseRepository.save(programmingExercise);
                var buildScript = aeolusBuildScriptGenerationService.get().getScript(programmingExercise);
                if (buildScript == null) {
                    throw new RuntimeException("Failed to migrate solution participation because the build script is null.");
                }
                programmingExercise.setBuildScript(buildScript);
                programmingExerciseRepository.save(programmingExercise);
            }
            catch (Exception e) {
                log.error("Failed to migrate solution participation with id {}", solutionParticipation.getId(), e);
                errorList.add(solutionParticipation);
            }
        }
    }

    /**
     * LocalCI needs some special treatment for some exercises. This method modifies the windfile to make it work flawlessly with LocalCI.
     *
     * @param windfile the windfile to modify
     */
    private void makeWindfileLocalCIOptimized(Windfile windfile) {
        var actions = windfile.getActions();
        for (Action action : actions) {
            if (action instanceof ScriptAction scriptAction) {
                var script = scriptAction.getScript();
                if (script.trim().contains("./gradlew clean test")) {
                    scriptAction.setScript(script.replace("./gradlew clean test", "chmod +x ./gradlew\n./gradlew clean test"));
                }
                else if (script.trim().contains("chmod -R 777 ${WORKDIR}")) {
                    scriptAction.setPlatform("bamboo");
                }
            }
        }
    }

    @Override
    public String author() {
        return "reschandreas";
    }

    @Override
    public String date() {
        return "20240104_195600";
    }
}
