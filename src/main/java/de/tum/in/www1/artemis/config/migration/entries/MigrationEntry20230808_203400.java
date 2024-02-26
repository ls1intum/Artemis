package de.tum.in.www1.artemis.config.migration.entries;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.config.Constants.SOLUTION_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;
import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDuration;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;

@Profile(PROFILE_CORE)
@Component
public class MigrationEntry20230808_203400 extends ProgrammingExerciseMigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 10;

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within nine hours. Aborting migration.";

    private static final int TIMEOUT_IN_HOURS = 9;

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20230808_203400.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final Optional<CIVCSMigrationService> ciMigrationService;

    private final Optional<VersionControlService> versionControlService;

    private final Environment environment;

    private final UriService uriService;

    private static final List<String> MIGRATABLE_PROFILES = List.of("bamboo", "gitlab", "jenkins");

    public MigrationEntry20230808_203400(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            Optional<CIVCSMigrationService> ciMigrationService, Optional<VersionControlService> versionControlService, Environment environment, UriService uriService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.ciMigrationService = ciMigrationService;
        this.versionControlService = versionControlService;
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

        try {
            ciMigrationService.orElseThrow().checkPrerequisites();
        }
        catch (ContinuousIntegrationException e) {
            log.error("Can not run migration because the prerequisites for it to succeed are not met: {}", e.getMessage());
            throw e;
        }
        var programmingExerciseCount = programmingExerciseRepository.count();
        var studentCount = ciMigrationService.orElseThrow().getPageableStudentParticipations(programmingExerciseStudentParticipationRepository, Pageable.unpaged())
                .getTotalElements();

        var totalCount = programmingExerciseCount * 2 + studentCount; // seconds

        log.info("Will migrate {} programming exercises and {} student participations now. Stay tuned! Estimate duration of the migration: {}", programmingExerciseCount,
                studentCount, formatDuration(totalCount));
        log.info("Artemis will be available again after the migration has finished.");

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

                // 2nd step: check if the default branch exists, this cleans up the database a bit and fixes the effects of a bug that
                // we had in the past
                // note: this method calls directly saves the participation in the database with the updated branch
                String branch = versionControlService.orElseThrow().getOrRetrieveBranchOfExercise(participation.getProgrammingExercise());
                if (branch == null || branch.isEmpty()) {
                    log.warn("Failed to get default branch for template of exercise {} with buildPlanId {}, will abort migration for this Participation",
                            participation.getProgrammingExercise().getId(), participation.getBuildPlanId());
                    continue;
                }

                log.info("Migrating solution build plan with name {} for exercise {}",
                        participation.getProgrammingExercise().getProjectKey() + "-" + participation.getBuildPlanId(), participation.getProgrammingExercise().getId());

                var startMs = System.currentTimeMillis();
                List<AuxiliaryRepository> auxiliaryRepositories = getAuxiliaryRepositories(participation.getProgrammingExercise().getId());

                migrateSolutionBuildPlan(participation, auxiliaryRepositories);

                migrateTestRepository(participation);
                log.info("Migrated solution build plan for exercise {} in {}ms", participation.getProgrammingExercise().getId(), System.currentTimeMillis() - startMs);
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

                log.info("Migrating build plan with name {} for exercise {}", participation.getProgrammingExercise().getProjectKey() + "-" + participation.getBuildPlanId(),
                        participation.getProgrammingExercise().getId());

                // 2nd step: check if the default branch exists, this cleans up the database a bit and fixes the effects of a bug that
                // we had in the past
                // note: this method calls directly saves the participation in the database with the updated branch
                String branch = versionControlService.orElseThrow().getOrRetrieveBranchOfExercise(participation.getProgrammingExercise());
                if (branch == null || branch.isEmpty()) {
                    log.warn("Failed to get default branch for template of exercise {} with buildPlanId {}, will abort migration for this Participation",
                            participation.getProgrammingExercise().getId(), participation.getBuildPlanId());
                    // CANCEL THE SUBSEQUENT OPERATIONS FOR THIS PROGRAMMING EXERCISE
                    continue;
                }
                var startMs = System.currentTimeMillis();
                List<AuxiliaryRepository> auxiliaryRepositories = getAuxiliaryRepositories(participation.getProgrammingExercise().getId());

                migrateTemplateBuildPlan(participation, auxiliaryRepositories);

                migrateTestRepository(participation);
                log.info("Migrated template build plan for exercise {} in {}ms", participation.getProgrammingExercise().getId(), System.currentTimeMillis() - startMs);
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

                // 2nd step: check if the default branch exists, this cleans up the database a bit and fixes the effects of a bug that we had in the past
                // note: this method calls directly saves the participation in the database with the updated branch in case it was not available
                String branch = versionControlService.orElseThrow().getOrRetrieveBranchOfStudentParticipation(participation);
                if (branch == null || branch.isEmpty()) {
                    log.warn("Failed to get default branch for template of exercise {} with buildPlanId {}, will abort migration for this Participation",
                            participation.getProgrammingExercise().getId(), participation.getBuildPlanId());
                    continue;
                }

                log.info("Migrating student participation with buildPlanId {} for exercise {}", participation.getBuildPlanId(), participation.getProgrammingExercise().getId());

                var startMs = System.currentTimeMillis();
                List<AuxiliaryRepository> auxiliaryRepositories = getAuxiliaryRepositories(participation.getProgrammingExercise().getId());

                migrateStudentBuildPlan(participation, auxiliaryRepositories);
                log.info("Migrated student build plan for exercise {} in {}ms", participation.getProgrammingExercise().getId(), System.currentTimeMillis() - startMs);
            }
            catch (Exception e) {
                log.warn("Failed to migrate student build plan for exercise {} with buildPlanId {}", participation.getProgrammingExercise().getId(), participation.getBuildPlanId(),
                        e);
                errorList.add(participation);
            }
        }
    }

    /**
     * Returns a list of auxiliary repositories for the given exercise.
     *
     * @param exerciseId The id of the exercise
     * @return A list of auxiliary repositories, or an empty list if the migration service does not support auxiliary repositories
     */
    private List<AuxiliaryRepository> getAuxiliaryRepositories(Long exerciseId) {
        if (ciMigrationService.orElseThrow().supportsAuxiliaryRepositories()) {
            return auxiliaryRepositoryRepository.findByExerciseId(exerciseId);
        }
        return new ArrayList<>();
    }

    /**
     * Migrates a single test repository. This method is idempotent, i.e. if you invoke it multiple times, it would still work fine
     *
     * @param participation The participation to migrate, needed so we can map the error to the participation
     */
    private void migrateTestRepository(AbstractBaseProgrammingExerciseParticipation participation) {
        var exercise = participation.getProgrammingExercise();
        if (exercise.getTestRepositoryUri() == null) {
            /*
             * when the test repository uri is null, we don't have to migrate the test build plan, saving multiple API calls
             * this is also only needed for Jenkins, as Bamboo does not have a separate test build plan
             */
            return;
        }
        try {
            ciMigrationService.orElseThrow().removeWebHook(exercise.getVcsTestRepositoryUri());
        }
        catch (Exception e) {
            log.warn("Failed to delete build triggers in test repository for exercise {} with test repository {}", exercise.getId(), exercise.getVcsTestRepositoryUri(), e);
            errorList.add(participation);
        }
    }

    /**
     * Migrates a single solution build plan.
     *
     * @param participation         The participation to migrate
     * @param auxiliaryRepositories The auxiliary repositories to migrate
     */
    private void migrateStudentBuildPlan(ProgrammingExerciseStudentParticipation participation, List<AuxiliaryRepository> auxiliaryRepositories) {
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
        try {
            ciMigrationService.orElseThrow().deleteBuildTriggers(exercise.getProjectKey(), participation.getBuildPlanId(), repositoryUri);
        }
        catch (Exception e) {
            log.warn("Failed to delete build triggers for studentParticipationId {} with buildPlanId {} of exerciseId {} ", participation.getId(), participation.getBuildPlanId(),
                    exercise.getId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(participation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, String.valueOf(repositoryUri),
                    participation.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace assignment repository in student build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", participation.getId(),
                    participation.getBuildPlanId(), exercise.getId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(participation.getBuildPlanId(), TEST_REPO_NAME, exercise.getTestRepositoryUri(),
                    participation.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace tests repository in student build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", participation.getId(),
                    participation.getBuildPlanId(), exercise.getId(), e);
            errorList.add(participation);
        }
        // NOTE: this handles an edge case with HASKELL exercises, where the solution repository is checked out in the student build plan
        try {
            // we dont have the solution repository uri in the student participation, so we have to get it from the repository service
            var solutionRepositoryUri = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).get().getVcsRepositoryUri();
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(participation.getBuildPlanId(), SOLUTION_REPO_NAME,
                    uriService.getPlainUriFromRepositoryUri(solutionRepositoryUri), participation.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace solution repository in student build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", participation.getId(),
                    participation.getBuildPlanId(), exercise.getId(), e);
            errorList.add(participation);
        }
        for (var auxiliary : auxiliaryRepositories) {
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(participation.getBuildPlanId(), auxiliary.getName(), auxiliary.getRepositoryUri(),
                        participation.getBranch());
            }
            catch (Exception e) {
                log.warn("Failed to replace {} repository in student build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", auxiliary.getName(),
                        participation.getId(), participation.getBuildPlanId(), exercise.getId(), e);
                errorList.add(participation);
            }
        }
        try {
            ciMigrationService.orElseThrow().overrideRepositoriesToCheckout(participation.getBuildPlanId(), auxiliaryRepositories, exercise.getProgrammingLanguage());
        }
        catch (Exception e) {
            log.warn("Failed to replace repositories in student build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", participation.getId(),
                    participation.getBuildPlanId(), exercise.getId(), e);
            errorList.add(participation);
        }
    }

    /**
     * Migrates a single solution build plan.
     *
     * @param participation         The participation to migrate
     * @param auxiliaryRepositories The auxiliary repositories to migrate
     */
    private void migrateSolutionBuildPlan(AbstractBaseProgrammingExerciseParticipation participation, List<AuxiliaryRepository> auxiliaryRepositories) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUri());
        }
        catch (Exception e) {
            log.warn("Failed to migrate solution build plan for exercise id {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().deleteBuildTriggers(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUri());
        }
        catch (Exception e) {
            log.warn("Failed to delete solution build triggers for exercise {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getSolutionBuildPlanId(), ASSIGNMENT_REPO_NAME, exercise.getSolutionRepositoryUri(),
                    exercise.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace solution repository in template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getSolutionBuildPlanId(), TEST_REPO_NAME, exercise.getTestRepositoryUri(), exercise.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace tests repository in solution build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(participation.getBuildPlanId(), SOLUTION_REPO_NAME, exercise.getSolutionRepositoryUri(),
                    exercise.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace solution repository in student build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", participation.getId(),
                    participation.getBuildPlanId(), exercise.getId(), e);
            errorList.add(participation);
        }
        for (var auxiliary : auxiliaryRepositories) {
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getSolutionBuildPlanId(), auxiliary.getName(), auxiliary.getRepositoryUri(),
                        exercise.getBranch());
            }
            catch (Exception e) {
                log.warn("Failed to replace {} repository in solution build plan for exercise {}  with buildPlanId {}", auxiliary.getName(), exercise.getId(),
                        exercise.getSolutionBuildPlanId(), e);
                errorList.add(participation);
            }
        }
        try {
            ciMigrationService.orElseThrow().overrideRepositoriesToCheckout(exercise.getSolutionBuildPlanId(), auxiliaryRepositories, exercise.getProgrammingLanguage());
        }
        catch (Exception e) {
            log.warn("Failed to replace repositories in solution build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getSolutionBuildPlanId(), e);
            errorList.add(participation);
        }
    }

    /**
     * Migrates a single template build plan.
     *
     * @param participation         The participation to migrate
     * @param auxiliaryRepositories The auxiliary repositories to migrate
     */
    private void migrateTemplateBuildPlan(AbstractBaseProgrammingExerciseParticipation participation, List<AuxiliaryRepository> auxiliaryRepositories) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUri());
        }
        catch (Exception e) {
            log.warn("Failed to migrate template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().deleteBuildTriggers(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUri());
        }
        catch (Exception e) {
            log.warn("Failed to delete template build triggers for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getTemplateBuildPlanId(), ASSIGNMENT_REPO_NAME, exercise.getTemplateRepositoryUri(),
                    exercise.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace template repository for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorList.add(participation);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getTemplateBuildPlanId(), TEST_REPO_NAME, exercise.getTestRepositoryUri(), exercise.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace tests repository in template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorList.add(participation);
        }
        // NOTE: this handles an edge case with HASKELL exercises, where the solution repository is checked out in the student build plan
        try {
            // we do not have the solution repository uri in the template participation, so we have to get it from the repository service
            var solutionRepositoryUri = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).get().getVcsRepositoryUri();
            ciMigrationService.orElseThrow().overrideBuildPlanRepository(participation.getBuildPlanId(), SOLUTION_REPO_NAME,
                    uriService.getPlainUriFromRepositoryUri(solutionRepositoryUri), exercise.getBranch());
        }
        catch (Exception e) {
            log.warn("Failed to replace solution repository in template build plan for studentParticipationId {} with buildPlanId {} of exerciseId {} ", participation.getId(),
                    participation.getBuildPlanId(), exercise.getId(), e);
            errorList.add(participation);
        }
        for (var auxiliary : auxiliaryRepositories) {
            try {
                ciMigrationService.orElseThrow().overrideBuildPlanRepository(exercise.getTemplateBuildPlanId(), auxiliary.getName(), auxiliary.getRepositoryUri(),
                        exercise.getBranch());
            }
            catch (Exception e) {
                log.warn("Failed to replace auxiliary repository {} in template build plan for exercise {} with buildPlanId {}", auxiliary.getName(), exercise.getId(),
                        exercise.getTemplateBuildPlanId(), e);
                errorList.add(participation);
            }
        }
        try {
            ciMigrationService.orElseThrow().overrideRepositoriesToCheckout(exercise.getTemplateBuildPlanId(), auxiliaryRepositories, exercise.getProgrammingLanguage());
        }
        catch (Exception e) {
            log.error("Failed to replace repositories in template build plan for exercise {} with buildPlanId {}", exercise.getId(), exercise.getTemplateBuildPlanId(), e);
            errorList.add(participation);
        }
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
