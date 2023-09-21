package de.tum.in.www1.artemis.config.migration.entries;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;

@Component
public class MigrationEntry20230920_181600 extends MigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 10;

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within nine hours. Aborting migration.";

    private static final int TIMEOUT_IN_HOURS = 9;

    private final Logger log = LoggerFactory.getLogger(MigrationEntry20230920_181600.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final Optional<CIVCSMigrationService> ciMigrationService;

    private final Environment environment;

    private final UrlService urlService = new UrlService();

    private final CopyOnWriteArrayList<ProgrammingExerciseParticipation> errorList = new CopyOnWriteArrayList<>();

    private static final List<String> MIGRATABLE_PROFILES = List.of("bamboo");

    public MigrationEntry20230920_181600(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            Optional<CIVCSMigrationService> ciMigrationService, Optional<VersionControlService> versionControlService, Environment environment) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.ciMigrationService = ciMigrationService;
        this.environment = environment;
    }

    @Override
    public void execute() {
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        if (activeProfiles.stream().noneMatch(MIGRATABLE_PROFILES::contains)) {
            log.info("Migration will be skipped and marked run because the system does not support a tech-stack that requires this migration: {}", activeProfiles);
            return;
        }

        var programmingExerciseCount = programmingExerciseRepository.count();
        var studentCount = ciMigrationService.orElseThrow().getPageableStudentParticipations(programmingExerciseStudentParticipationRepository, Pageable.unpaged())
                .getTotalElements();

        log.info("Will migrate {} programming exercises and {} student participations now.", programmingExerciseCount, studentCount);

        // Number of full batches. The last batch might be smaller
        long totalFullBatchCount = programmingExerciseCount / BATCH_SIZE;
        int threadCount = (int) Math.max(1, Math.min(totalFullBatchCount, MAX_THREAD_COUNT));

        // Use fixed thread pool to prevent loading too many exercises into memory at once
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        /*
         * migrate the solution participations first, then the template participations, then the student participations
         */
        var solutionCount = solutionProgrammingExerciseParticipationRepository.count();
        log.info("Found {} solution participations to migrate.", solutionCount);
        for (int currentPageStart = 0; currentPageStart < solutionCount; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            var solutionParticipationPage = solutionProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Will migrate {} solution participations in batch.", solutionParticipationPage.getNumberOfElements());
            var solutionParticipationsPartitions = Lists.partition(solutionParticipationPage.toList(), threadCount);
            for (var solutionParticipations : solutionParticipationsPartitions) {
                executorService.submit(() -> migrateSolutions(solutionParticipations));
            }
        }

        log.info("Submitted all solution participations to thread pool for migration.");
        /*
         * migrate the template participations
         */
        var templateCount = templateProgrammingExerciseParticipationRepository.count();
        log.info("Found {} template participations to migrate", templateCount);
        for (int currentPageStart = 0; currentPageStart < templateCount; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            var templateParticipationPage = templateProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Will migrate {} template programming exercises in batch.", templateParticipationPage.getNumberOfElements());
            var templateParticipationsPartitions = Lists.partition(templateParticipationPage.toList(), threadCount);
            for (var templateParticipations : templateParticipationsPartitions) {
                executorService.submit(() -> migrateTemplates(templateParticipations));
            }
        }

        log.info("Submitted all template participations to thread pool for migration.");
        /*
         * migrate the student participations
         */
        log.info("Found {} student programming exercise participations with build plans to migrate.", studentCount);
        for (int currentPageStart = 0; currentPageStart < studentCount; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            Page<ProgrammingExerciseStudentParticipation> studentParticipationPage = ciMigrationService.orElseThrow()
                    .getPageableStudentParticipations(programmingExerciseStudentParticipationRepository, pageable);
            log.info("Will migrate {} student programming exercise participations in batch.", studentParticipationPage.getNumberOfElements());
            var studentPartitionsPartitions = Lists.partition(studentParticipationPage.toList(), threadCount);
            for (var studentParticipations : studentPartitionsPartitions) {
                executorService.submit(() -> migrateStudents(studentParticipations));
            }
        }

        // Wait for all threads to finish
        executorService.shutdown();

        try {
            boolean finished = executorService.awaitTermination(TIMEOUT_IN_HOURS, TimeUnit.HOURS);
            if (!finished) {
                log.error(ERROR_MESSAGE);
                if (executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                    log.error("Failed to cancel all migration threads. Some threads are still running.");
                }
                throw new RuntimeException(ERROR_MESSAGE);
            }
        }
        catch (InterruptedException e) {
            log.error(ERROR_MESSAGE);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        log.info("Finished migrating programming exercises and student participations");
        evaluateErrorList();
    }

    /**
     * Executes the migration for a batch of participations.
     *
     * @param participations The participations to migrate
     */
    private void migrateSolutions(List<SolutionProgrammingExerciseParticipation> participations) {
        for (var participation : participations) {
            try {
                // 1st step: check if the build plan exists, this cleans up the database a bit
                boolean buildPlanExists = ciMigrationService.orElseThrow().buildPlanExists(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId());
                if (!buildPlanExists) {
                    log.warn("Build plan {} does not exist for exercise {}", participation.getBuildPlanId(), participation.getProgrammingExercise().getId());
                    log.info("Skipping migration for exercise {} and setting build_plan_id to null", participation.getProgrammingExercise().getId());
                    participation.setBuildPlanId(null);
                    solutionProgrammingExerciseParticipationRepository.save(participation);
                    continue;
                }

                log.info("Migrating solution build plan with name {} for exercise {}",
                        participation.getProgrammingExercise().getProjectKey() + "-" + participation.getBuildPlanId(), participation.getProgrammingExercise().getId());

                migrateSolutionBuildPlan(participation);
            }
            catch (Exception e) {
                log.warn("Failed to migrate solution build plan for exercise {} with buildPlanId {}", participation.getProgrammingExercise().getId(),
                        participation.getBuildPlanId(), e);
                errorList.add(participation);
            }
        }
    }

    /**
     * Migrates the build plans of the given participations.
     *
     * @param participations The participations to migrate
     */
    private void migrateTemplates(List<TemplateProgrammingExerciseParticipation> participations) {
        for (var participation : participations) {
            try {
                // 1st step: check if the build plan exists, this cleans up the database a bit
                boolean buildPlanExists = ciMigrationService.orElseThrow().buildPlanExists(participation.getProgrammingExercise().getProjectKey(),
                        participation.getProgrammingExercise().getTemplateBuildPlanId());
                if (!buildPlanExists) {
                    log.warn("Build plan {} does not exist for exercise {}", participation.getProgrammingExercise().getTemplateBuildPlanId(),
                            participation.getProgrammingExercise().getId());
                    log.info("Skipping migration for template of exercise {} and setting build_plan_id to null", participation.getProgrammingExercise().getId());
                    participation.setBuildPlanId(null);
                    templateProgrammingExerciseParticipationRepository.save(participation);
                    // CANCEL THE SUBSEQUENT OPERATIONS FOR THIS PROGRAMMING EXERCISE
                    continue;
                }

                log.info("Migrating template build plan with name {} for exercise {}",
                        participation.getProgrammingExercise().getProjectKey() + "-" + participation.getBuildPlanId(), participation.getProgrammingExercise().getId());

                migrateTemplateBuildPlan(participation);
            }
            catch (Exception e) {
                log.warn("Failed to migrate template build plan for exercise {} with buildPlanId {}", participation.getProgrammingExercise().getId(),
                        participation.getBuildPlanId(), e);
                errorList.add(participation);
            }
        }
    }

    /**
     * Migrates the build plans of the given participations.
     *
     * @param participations The participations to migrate
     */
    private void migrateStudents(List<ProgrammingExerciseStudentParticipation> participations) {
        for (var participation : participations) {
            try {
                // 1st step: check if the build plan exists, this cleans up the database a bit
                boolean buildPlanExists = ciMigrationService.orElseThrow().buildPlanExists(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId());
                if (!buildPlanExists) {
                    log.warn("Build plan {} does not exist for exercise {}", participation.getBuildPlanId(), participation.getProgrammingExercise().getId());
                    log.info("Skipping migration for exercise {} and setting build_plan_id to null", participation.getProgrammingExercise().getId());
                    participation.setBuildPlanId(null);
                    programmingExerciseStudentParticipationRepository.save(participation);
                    continue;
                }

                log.info("Migrating student participation with buildPlanId {} for exercise {}", participation.getBuildPlanId(), participation.getProgrammingExercise().getId());

                migrateStudentBuildPlan(participation);
            }
            catch (Exception e) {
                log.warn("Failed to migrate student build plan for exercise {} with buildPlanId {}", participation.getProgrammingExercise().getId(), participation.getBuildPlanId(),
                        e);
                errorList.add(participation);
            }
        }
    }

    /**
     * Migrates a single solution build plan.
     *
     * @param participation The participation to migrate
     */
    private void migrateStudentBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        VcsRepositoryUrl repositoryUrl;
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        try {
            repositoryUrl = new VcsRepositoryUrl(urlService.getPlainUrlFromRepositoryUrl(participation.getVcsRepositoryUrl()));
        }
        catch (URISyntaxException e) {
            log.warn("Failed to convert git url {} for studentParticipationId {} exerciseId {} with buildPlanId {}, will abort migration for this Participation",
                    participation.getVcsRepositoryUrl(), participation.getId(), exercise.getId(), participation.getBuildPlanId(), e);
            errorList.add(participation);
            return;
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), participation.getBuildPlanId(), repositoryUrl);
        }
        catch (Exception e) {
            log.warn("Failed to migrate build plan notifications for studentParticipationId {} with buildPlanId {} of exerciseId {} ", participation.getId(),
                    participation.getBuildPlanId(), exercise.getId(), e);
            errorList.add(participation);
        }
    }

    /**
     * Migrates a single solution build plan.
     *
     * @param participation The participation to migrate
     */
    private void migrateSolutionBuildPlan(AbstractBaseProgrammingExerciseParticipation participation) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUrl());
        }
        catch (Exception e) {
            log.warn("Failed to migrate solution build plan for exercise id {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorList.add(participation);
        }
    }

    /**
     * Migrates a single template build plan.
     *
     * @param participation The participation to migrate
     */
    private void migrateTemplateBuildPlan(AbstractBaseProgrammingExerciseParticipation participation) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUrl());
        }
        catch (Exception e) {
            log.warn("Failed to migrate template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorList.add(participation);
        }
    }

    /**
     * Evaluates the error map and prints the errors to the log.
     */
    private void evaluateErrorList() {
        if (errorList.isEmpty()) {
            log.info("Successfully migrated all programming exercises");
            return;
        }

        List<Long> failedTemplateExercises = errorList.stream().filter(participation -> participation instanceof TemplateProgrammingExerciseParticipation)
                .map(participation -> participation.getProgrammingExercise().getId()).toList();
        List<Long> failedSolutionExercises = errorList.stream().filter(participation -> participation instanceof SolutionProgrammingExerciseParticipation)
                .map(participation -> participation.getProgrammingExercise().getId()).toList();
        List<Long> failedStudentParticipations = errorList.stream().filter(participation -> participation instanceof ProgrammingExerciseStudentParticipation)
                .map(ParticipationInterface::getId).toList();

        log.error("{} failures during migration", errorList.size());
        // print each participation in a single line in the long to simplify reviewing the issues
        log.error("Errors occurred for the following participations: \n{}", errorList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        log.error("Failed to migrate template build plan for exercises: {}", failedTemplateExercises);
        log.error("Failed to migrate solution build plan for exercises: {}", failedSolutionExercises);
        log.error("Failed to migrate students participations: {}", failedStudentParticipations);
        log.warn("Please check the logs for more information. If the issues are related to the external VCS/CI system, fix the issues and rerun the migration. or "
                + "fix the build plans yourself and mark the migration as run. The migration can be rerun by deleting the migration entry in the database table containing "
                + "the migration with author: " + author() + " and date_string: " + date() + " and then restarting Artemis.");
    }

    @Override
    public String author() {
        return "julian-christl";
    }

    @Override
    public String date() {
        return "20230920_181600";
    }
}
