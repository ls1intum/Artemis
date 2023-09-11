package de.tum.in.www1.artemis.config.migration.entries;

import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.hazelcast.spi.exception.RestClientException;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UrlService;

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

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final Optional<CIVCSMigrationService> ciMigrationService;

    private final UrlService urlService = new UrlService();

    public MigrationEntry20230808_203400(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            Optional<CIVCSMigrationService> ciMigrationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.ciMigrationService = ciMigrationService;
    }

    @Override
    public void execute() {
        try {
            ciMigrationService.orElseThrow().checkPrerequisites();
        }
        catch (ContinuousIntegrationException e) {
            log.error("Can not run migration because the prerequisites for it to succeed are not met: {}", e.getMessage());
            throw e;
        }
        long exerciseCount = programmingExerciseRepository.count();
        if (exerciseCount == 0) {
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
            boolean finished = executorService.awaitTermination(3, TimeUnit.HOURS);
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
        for (int batchStartIndex = (int) start, batch = 0; batchStartIndex < end; batchStartIndex++, batch = batch + BATCH_SIZE) {
            var batchStart = System.currentTimeMillis();
            Pageable pageable = PageRequest.of(batchStartIndex, BATCH_SIZE);

            // TODO: in the unlikely case the buildPlanId is null, we could skip the template participation
            var templateParticipationPage = templateProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Found {} template programming exercises to migrate in batch", templateParticipationPage.getTotalElements());
            templateParticipationPage.map(templateParticipation -> {
                if (!ciMigrationService.orElseThrow().buildPlanExists(templateParticipation.getProgrammingExercise().getProjectKey(),
                        templateParticipation.getProgrammingExercise().getTemplateBuildPlanId())) {
                    log.warn("Build plan {} does not exist for exercise {}", templateParticipation.getProgrammingExercise().getTemplateBuildPlanId(),
                            templateParticipation.getProgrammingExercise().getId());
                    log.info("Skipping migration for template of exercise {} and setting build_plan_id to null", templateParticipation.getProgrammingExercise().getId());
                    templateParticipation.setBuildPlanId(null);
                    templateProgrammingExerciseParticipationRepository.save(templateParticipation);
                    return null;
                }
                log.info("Migrating build plan with name {} for exercise {}",
                        templateParticipation.getProgrammingExercise().getProjectKey() + "-" + templateParticipation.getBuildPlanId(),
                        templateParticipation.getProgrammingExercise().getId());
                return templateParticipation;
            }).filter(Objects::nonNull).forEach(templateParticipation -> {
                var startMs = System.currentTimeMillis();
                List<AuxiliaryRepository> auxiliaryRepositories = new ArrayList<>();
                if (ciMigrationService.orElseThrow().supportsAuxiliaryRepositories()) {
                    auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(templateParticipation.getProgrammingExercise().getId());
                }
                migrateTemplateBuildPlan(templateParticipation.getProgrammingExercise(), auxiliaryRepositories, errorMap);

                migrateTestBuildPlan(templateParticipation.getProgrammingExercise(), errorMap);
                log.info("Migrated template build plan for exercise {} in {}ms", templateParticipation.getProgrammingExercise().getId(), System.currentTimeMillis() - startMs);
            });

            // TODO: in the unlikely case the buildPlanId is null, we could skip the solution participation
            var solutionParticipationPage = solutionProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Found {} solution programming exercises to migrate in batch", solutionParticipationPage.getTotalElements());
            solutionParticipationPage.map(solutionParticipation -> {
                if (!ciMigrationService.orElseThrow().buildPlanExists(solutionParticipation.getProgrammingExercise().getProjectKey(), solutionParticipation.getBuildPlanId())) {
                    log.warn("Build plan {} does not exist for exercise {}", solutionParticipation.getBuildPlanId(), solutionParticipation.getProgrammingExercise().getId());
                    log.info("Skipping migration for exercise {} and setting build_plan_id to null", solutionParticipation.getProgrammingExercise().getId());
                    solutionParticipation.setBuildPlanId(null);
                    solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
                    return null;
                }
                return solutionParticipation;
            }).filter(Objects::nonNull).forEach(solutionParticipation -> {
                var startMs = System.currentTimeMillis();
                List<AuxiliaryRepository> auxiliaryRepositories = new ArrayList<>();
                if (ciMigrationService.orElseThrow().supportsAuxiliaryRepositories()) {
                    auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(solutionParticipation.getProgrammingExercise().getId());
                }
                migrateSolutionBuildPlan(solutionParticipation.getProgrammingExercise(), auxiliaryRepositories, errorMap);

                migrateTestBuildPlan(solutionParticipation.getProgrammingExercise(), errorMap);
                log.info("Migrated solution build plan for exercise {} in {}ms", solutionParticipation.getProgrammingExercise().getId(), System.currentTimeMillis() - startMs);
            });

            Page<ProgrammingExerciseStudentParticipation> studentParticipationPage = ciMigrationService.orElseThrow()
                    .getPageableStudentParticipations(programmingExerciseStudentParticipationRepository, pageable);
            log.info("Found {} student programming exercises to migrate in batch", studentParticipationPage.getTotalElements());
            studentParticipationPage.map(studentParticipation -> {
                if (!ciMigrationService.orElseThrow().buildPlanExists(studentParticipation.getProgrammingExercise().getProjectKey(), studentParticipation.getBuildPlanId())) {
                    log.warn("Build plan {} does not exist for exercise {}", studentParticipation.getBuildPlanId(), studentParticipation.getProgrammingExercise().getId());
                    log.info("Skipping migration for exercise {} and setting build_plan_id to null", studentParticipation.getProgrammingExercise().getId());
                    studentParticipation.setBuildPlanId(null);
                    programmingExerciseStudentParticipationRepository.save(studentParticipation);
                    return null;
                }
                return studentParticipation;
            }).filter(Objects::nonNull).forEach(studentParticipation -> {
                var startMs = System.currentTimeMillis();
                List<AuxiliaryRepository> auxiliaryRepositories = new ArrayList<>();
                if (ciMigrationService.orElseThrow().supportsAuxiliaryRepositories()) {
                    auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(studentParticipation.getProgrammingExercise().getId());
                }
                migrateStudentBuildPlan(studentParticipation.getProgrammingExercise(), studentParticipation, auxiliaryRepositories, errorMap);
                log.info("Migrated student build plan for exercise {} in {}ms", studentParticipation.getProgrammingExercise().getId(), System.currentTimeMillis() - startMs);
            });

            log.info("Migrated {} / {} programming exercises in current thread", (Math.min(exerciseCount, batch + 1)), exerciseCount);
            log.info("Migrated batch in {}ms", System.currentTimeMillis() - batchStart);
        }
    }

    /**
     * Migrates a single test build plan.
     *
     * @param exercise The exercise to migrate
     * @param errorMap The error map to write errors to
     */
    private void migrateTestBuildPlan(ProgrammingExercise exercise, Map<ProgrammingExercise, Boolean> errorMap) {
        if (exercise.getTestRepositoryUrl() == null) {
            /*
             * when the test repository url is null, we don't have to migrate the test build plan, saving multiple API calls
             * this is also only needed for Jenkins, as Bamboo does not have a separate test build plan
             */
            return;
        }
        try {
            ciMigrationService.orElseThrow().deleteBuildTriggers(null, null, exercise.getVcsTestRepositoryUrl());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to delete test build triggers for exercise {} with test repository {}", exercise.getId(), exercise.getVcsTestRepositoryUrl(), e);
            errorMap.put(exercise, true);
        }
    }

    /**
     * Migrates a single solution build plan.
     *
     * @param exercise The exercise to migrate
     * @param errorMap The error map to write errors to
     */
    private void migrateStudentBuildPlan(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation studentParticipation,
            List<AuxiliaryRepository> auxiliaryRepositories, Map<ProgrammingExercise, Boolean> errorMap) {
        VcsRepositoryUrl repositoryUrl;
        try {
            repositoryUrl = new VcsRepositoryUrl(urlService.getPlainUrlFromRepositoryUrl(studentParticipation.getVcsRepositoryUrl()));
        }
        catch (URISyntaxException e) {
            log.warn("Failed to convert git url {} for studentParticipationId {} exerciseId {} with buildPlanId {}, will abort migration for this Participation",
                    studentParticipation.getVcsRepositoryUrl(), studentParticipation.getId(), exercise.getId(), studentParticipation.getBuildPlanId(), e);
            errorMap.put(exercise, false);
            return;
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), studentParticipation.getBuildPlanId(), repositoryUrl);
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to migrate solution build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", studentParticipation.getId(),
                    studentParticipation.getBuildPlanId(), exercise.getId(), e);
            errorMap.put(exercise, false);
        }
        try {
            ciMigrationService.orElseThrow().deleteBuildTriggers(exercise.getProjectKey(), studentParticipation.getBuildPlanId(), repositoryUrl);
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to delete solution build triggers for studentParticipationId {} with buildPlanId {} of exerciseId {} ", studentParticipation.getId(),
                    studentParticipation.getBuildPlanId(), exercise.getId(), e);
            errorMap.put(exercise, false);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(studentParticipation.getBuildPlanId(), "assignment", String.valueOf(repositoryUrl), exercise.getBranch());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to replace solution repository in template build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ",
                    studentParticipation.getId(), studentParticipation.getBuildPlanId(), exercise.getId(), e);
            errorMap.put(exercise, false);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(studentParticipation.getBuildPlanId(), "tests", exercise.getTestRepositoryUrl(), exercise.getBranch());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to replace tests repository in solution build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", studentParticipation.getId(),
                    studentParticipation.getBuildPlanId(), exercise.getId(), e);
            errorMap.put(exercise, false);
        }
        for (var auxiliary : auxiliaryRepositories) {
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(studentParticipation.getBuildPlanId(), auxiliary.getName(), auxiliary.getRepositoryUrl(),
                        exercise.getBranch());
            }
            catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
                log.warn("Failed to replace {} repository in solution build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", auxiliary.getName(),
                        studentParticipation.getId(), studentParticipation.getBuildPlanId(), exercise.getId(), e);
                errorMap.put(exercise, true);
            }
        }
        try {
            ciMigrationService.orElseThrow().overrideRepositoriesToCheckout(studentParticipation.getBuildPlanId(), auxiliaryRepositories);
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to replace repositories in solution build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", studentParticipation.getId(),
                    studentParticipation.getBuildPlanId(), exercise.getId(), e);
            errorMap.put(exercise, false);
        }
    }

    /**
     * Migrates a single solution build plan.
     *
     * @param exercise The exercise to migrate
     * @param errorMap The error map to write errors to
     */
    private void migrateSolutionBuildPlan(ProgrammingExercise exercise, List<AuxiliaryRepository> auxiliaryRepositories, Map<ProgrammingExercise, Boolean> errorMap) {
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUrl());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to migrate solution build plan for exercise id {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorMap.put(exercise, false);
        }
        try {
            ciMigrationService.orElseThrow().deleteBuildTriggers(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUrl());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to delete solution build triggers for exercise {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorMap.put(exercise, false);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getSolutionBuildPlanId(), "assignment", exercise.getSolutionRepositoryUrl(),
                    exercise.getBranch());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to replace solution repository in template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorMap.put(exercise, false);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getSolutionBuildPlanId(), "tests", exercise.getTestRepositoryUrl(), exercise.getBranch());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to replace tests repository in solution build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorMap.put(exercise, false);
        }
        for (var auxiliary : auxiliaryRepositories) {
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getSolutionBuildPlanId(), auxiliary.getName(), auxiliary.getRepositoryUrl(),
                        exercise.getBranch());
            }
            catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
                log.warn("Failed to replace {} repository in solution build plan for exercise {}  with buildPlanId {}", auxiliary.getName(), exercise.getId(),
                        exercise.getSolutionBuildPlanId(), e);
                errorMap.put(exercise, false);
            }
        }
        try {
            ciMigrationService.orElseThrow().overrideRepositoriesToCheckout(exercise.getSolutionBuildPlanId(), auxiliaryRepositories);
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to replace repositories in solution build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorMap.put(exercise, false);
        }
    }

    /**
     * Migrates a single template build plan.
     *
     * @param exercise The exercise to migrate
     * @param errorMap The error map to write errors to
     */
    private void migrateTemplateBuildPlan(ProgrammingExercise exercise, List<AuxiliaryRepository> auxiliaryRepositories, Map<ProgrammingExercise, Boolean> errorMap) {
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUrl());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to migrate template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorMap.put(exercise, true);
        }
        try {
            ciMigrationService.orElseThrow().deleteBuildTriggers(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUrl());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to delete template build triggers for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorMap.put(exercise, true);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getTemplateBuildPlanId(), "assignment", exercise.getTemplateRepositoryUrl(),
                    exercise.getBranch());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to replace template repository for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorMap.put(exercise, true);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getTemplateBuildPlanId(), "tests", exercise.getTestRepositoryUrl(), exercise.getBranch());
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.warn("Failed to replace tests repository in template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorMap.put(exercise, true);
        }
        for (var auxiliary : auxiliaryRepositories) {
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getTemplateBuildPlanId(), auxiliary.getName(), auxiliary.getRepositoryUrl(),
                        exercise.getBranch());
            }
            catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
                log.warn("Failed to replace auxiliary repository {} in template build plan for exercise {} with buildPlanId {}", auxiliary.getName(), exercise.getId(),
                        exercise.getTemplateBuildPlanId(), e);
                errorMap.put(exercise, true);
            }
        }
        try {
            ciMigrationService.orElseThrow().overrideRepositoriesToCheckout(exercise.getTemplateBuildPlanId(), auxiliaryRepositories);
        }
        catch (VersionControlException | ContinuousIntegrationException | NoSuchElementException | RestClientException e) {
            log.error("Failed to replace repositories in template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorMap.put(exercise, true);
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
        log.error("Failed to migrate {} programming exercises", errorMap.size());
        log.error("Failed to migrate template build plan for exercises: {}", failedExerciseIdsForTemplates);
        log.error("Failed to migrate solution build plan for exercises: {}", failedExerciseIdsForSolutions);
        log.warn("Please check the logs for more information. Then either fix the issues and rerun the migration or "
                + "fix the build plans yourself and mark the migration as run.");
    }

    @Override
    public String author() {
        return "julian-christl";
    }

    @Override
    public String date() {
        return "20230808_203400";
    }
}
