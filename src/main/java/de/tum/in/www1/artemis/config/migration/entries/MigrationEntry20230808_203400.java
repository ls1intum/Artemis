package de.tum.in.www1.artemis.config.migration.entries;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.ci.CIMigrationService;

@Component
public class MigrationEntry20230808_203400 extends MigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 10;

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within one hour. Aborting migration.";

    private final Logger log = LoggerFactory.getLogger(MigrationEntry20230808_203400.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final Optional<CIMigrationService> ciMigrationService;

    public MigrationEntry20230808_203400(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<CIMigrationService> ciMigrationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.ciMigrationService = ciMigrationService;
    }

    @Override
    public void execute() {
        long exerciseCount = programmingExerciseRepository.count();
        if (exerciseCount == 0 || ciMigrationService.isEmpty()) {
            return;
        }

        log.info("Migrating {} programming exercises", exerciseCount);

        // Maps exercise to a boolean indicating whether the exercise had an issue with the template (true) or
        // solution (false) build plan
        // Didn't use an enum for simplicity of the migration
        Map<ProgrammingExercise, Boolean> errorMap = new HashMap<>();

        // Number of full batches. The last batch might be smaller
        long totalFullBatchCount = exerciseCount / BATCH_SIZE;
        long threadCount = Math.max(1, Math.min(totalFullBatchCount, MAX_THREAD_COUNT));
        long batchCountPerThread = totalFullBatchCount / threadCount;

        // Use fixed thread pool to prevent loading too many exercises into memory at once
        ExecutorService executorService = Executors.newFixedThreadPool((int) threadCount);

        long lastBatchEnd = totalFullBatchCount + 1;
        long lastBatchExerciseCount = batchCountPerThread * BATCH_SIZE + exerciseCount % BATCH_SIZE;

        for (int tc = 0; tc < threadCount; tc++) {
            boolean isLastThread = tc == threadCount - 1;
            long start = tc * batchCountPerThread;
            long end = (tc + 1) * batchCountPerThread;
            long exerciseCountForThread = batchCountPerThread * BATCH_SIZE;
            executorService.submit(() -> {
                Map<ProgrammingExercise, Boolean> threadErrorMap = new HashMap<>();
                if (isLastThread) {
                    executeBatchedMigration(start, lastBatchEnd, lastBatchExerciseCount, threadErrorMap);
                }
                else {
                    executeBatchedMigration(start, end, exerciseCountForThread, threadErrorMap);
                }
                synchronized (errorMap) {
                    errorMap.putAll(threadErrorMap);
                }
            });
        }

        // Wait for all threads to finish
        executorService.shutdown();

        try {
            boolean finished = executorService.awaitTermination(1, TimeUnit.HOURS);
            if (!finished) {
                log.error(ERROR_MESSAGE);
                throw new RuntimeException(ERROR_MESSAGE);
            }
        }
        catch (InterruptedException e) {
            log.error(ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

        evaluateErrorMap(errorMap);
    }

    /**
     * Executes the migration for a batch of programming exercises. Expected to be in a separate thread.
     *
     * @param start         The start index of the batch page
     * @param end           The end index of the batch page
     * @param exerciseCount The total number of exercises for this thread
     * @param errorMap      The error map to write errors to
     */
    private void executeBatchedMigration(long start, long end, long exerciseCount, Map<ProgrammingExercise, Boolean> errorMap) {
        // do batches
        for (int i = (int) start, j = 0; i < end; i++, j = j + BATCH_SIZE) {
            Pageable pageable = PageRequest.of(i, BATCH_SIZE);

            // TODO: in the unlikely case the buildPlanId is null, we could skip the template participation
            var templateParticipationPage = templateProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Found {} template programming exercise to migrate in batch", templateParticipationPage.getTotalElements());
            templateParticipationPage.forEach(templateParticipation -> {
                templateParticipation.getProgrammingExercise().setTemplateParticipation(templateParticipation);
                migrateExercise(templateParticipation.getProgrammingExercise(), errorMap);
            });

            // TODO: in the unlikely case the buildPlanId is null, we could skip the solution participation
            var solutionParticipationPage = solutionProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Found {} solution programming exercise to migrate in batch", solutionParticipationPage.getTotalElements());
            solutionParticipationPage.forEach(solutionParticipation -> {
                solutionParticipation.getProgrammingExercise().setSolutionParticipation(solutionParticipation);
                migrateExercise(solutionParticipation.getProgrammingExercise(), errorMap);
            });

            var studentParticipationPage = programmingExerciseStudentParticipationRepository.findAllWithBuildPlanId(pageable);
            log.info("Found {} student programming exercise to migrate in batch", studentParticipationPage.getTotalElements());
            var urlService = new UrlService();
            studentParticipationPage.forEach(studentParticipation -> {
                TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
                templateParticipation.setBuildPlanId(studentParticipation.getBuildPlanId());
                templateParticipation.setRepositoryUrl(urlService.getPlainUrlFromRepositoryUrl(studentParticipation.getVcsRepositoryUrl()));
                studentParticipation.getProgrammingExercise().setTemplateParticipation(templateParticipation);
                migrateExercise(studentParticipation.getProgrammingExercise(), errorMap);
            });

            log.info("Migrated {} / {} programming exercises in current thread", (Math.min(exerciseCount, j + 1)), exerciseCount);
        }
    }

    /**
     * Migrates a single programming exercise.
     *
     * @param exercise The exercise to migrate
     * @param errorMap The error map to write errors to
     */
    private void migrateExercise(ProgrammingExercise exercise, Map<ProgrammingExercise, Boolean> errorMap) {
        if (exercise.getTestRepositoryUrl() != null) {
            /*
             * when the test repository url is null, we don't have to migrate the test build plan, saving multiple API calls
             * this is also only needed for Jenkins, as Bamboo does not have a separate test build plan
             */
            try {
                ciMigrationService.orElseThrow().deleteBuildTriggers("not-needed-key", exercise.getVcsTestRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to delete test build triggers for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, true);
            }
        }
        if (exercise.getTemplateBuildPlanId() != null) {
            /*
             * when the template build plan id is null, we don't have to migrate the template build plan, saving multiple API calls
             */
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to migrate template build plan for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, true);
            }
            try {
                ciMigrationService.orElseThrow().deleteBuildTriggers(exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to delete template build triggers for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, true);
            }
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getTemplateBuildPlanId(), "assignment", exercise.getTemplateRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to replace template repository for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, true);
            }
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getTemplateBuildPlanId(), "tests", exercise.getTestRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to replace tests repository in template build plan for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, true);
            }
            try {
                ciMigrationService.orElseThrow().overrideRepositoriesToCheckout(exercise.getTemplateBuildPlanId());
            }
            catch (Exception e) {
                log.warn("Failed to replace repositories in template build plan for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, true);
            }
        }
        if (exercise.getSolutionBuildPlanId() != null) {
            /*
             * when the solution build plan id is null, we don't have to migrate the solution build plan, saving multiple API calls
             */
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to migrate solution build plan for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, false);
            }
            try {
                ciMigrationService.orElseThrow().deleteBuildTriggers(exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to delete solution build triggers for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, false);
            }
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getSolutionBuildPlanId(), "assignment", exercise.getSolutionRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to replace solution repository in template build plan for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, false);
            }
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getSolutionBuildPlanId(), "tests", exercise.getTestRepositoryUrl());
            }
            catch (Exception e) {
                log.warn("Failed to replace tests repository in solution build plan for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, false);
            }
            try {
                ciMigrationService.orElseThrow().overrideRepositoriesToCheckout(exercise.getSolutionBuildPlanId());
            }
            catch (Exception e) {
                log.warn("Failed to replace repositories in solution build plan for exercise {}", exercise.getId(), e);
                errorMap.put(exercise, false);
            }
        }
    }

    private void evaluateErrorMap(Map<ProgrammingExercise, Boolean> errorMap) {
        if (errorMap.isEmpty()) {
            return;
        }

        List<Long> failedExerciseIdsForTemplates = errorMap.entrySet().stream().filter(entry -> Boolean.TRUE.equals(entry.getValue())).map(entry -> entry.getKey().getId())
                .toList();
        List<Long> failedExerciseIdsForSolutions = errorMap.entrySet().stream().filter(entry -> Boolean.FALSE.equals(entry.getValue())).map(entry -> entry.getKey().getId())
                .toList();
        log.warn("Failed to migrate {} programming exercises", errorMap.size());
        log.warn("Failed to migrate template build plan for exercises: {}", failedExerciseIdsForTemplates);
        log.warn("Failed to migrate solution build plan for exercises: {}", failedExerciseIdsForSolutions);
        log.warn("Please check the logs for more information. Then either fix the issues and rerun the migration or "
                + "fix the build plans yourself and mark the migration as run.");
    }

    @Override
    public String author() {
        return "reschandreas";
    }

    @Override
    public String date() {
        return "20230904_162300";
    }
}
