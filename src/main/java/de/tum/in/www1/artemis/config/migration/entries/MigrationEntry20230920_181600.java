package de.tum.in.www1.artemis.config.migration.entries;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.UriService;

@Component
public class MigrationEntry20230920_181600 extends ProgrammingExerciseMigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 10;

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within nine hours. Aborting migration.";

    private static final int TIMEOUT_IN_HOURS = 9;

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20230920_181600.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final Optional<CIVCSMigrationService> ciMigrationService;

    private final Environment environment;

    private final UriService uriService;

    private static final List<String> MIGRATABLE_PROFILES = List.of("bamboo");

    public MigrationEntry20230920_181600(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<CIVCSMigrationService> ciMigrationService,
            Environment environment, UriService uriService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.ciMigrationService = ciMigrationService;
        this.environment = environment;
        this.uriService = uriService;
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

        shutdown(executorService, TIMEOUT_IN_HOURS, ERROR_MESSAGE);
        log.info("Finished migrating programming exercises and student participations");
        evaluateErrorList(programmingExerciseRepository);
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
        VcsRepositoryUri repositoryUri;
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        try {
            repositoryUri = new VcsRepositoryUri(uriService.getPlainUriFromRepositoryUri(participation.getVcsRepositoryUri()));
        }
        catch (URISyntaxException e) {
            log.warn("Failed to convert git url {} for studentParticipationId {} exerciseId {} with buildPlanId {}, will abort migration for this Participation",
                    participation.getVcsRepositoryUri(), participation.getId(), exercise.getId(), participation.getBuildPlanId(), e);
            errorList.add(participation);
            return;
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), participation.getBuildPlanId(), repositoryUri);
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
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUri());
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
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUri());
        }
        catch (Exception e) {
            log.warn("Failed to migrate template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorList.add(participation);
        }
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
