package de.tum.in.www1.artemis.config.migration.setups.localvc.gitlab;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.setups.ProgrammingExerciseMigrationEntry;
import de.tum.in.www1.artemis.config.migration.setups.localvc.LocalVCMigrationService;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCService;
import de.tum.in.www1.artemis.service.connectors.vcs.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

@Profile(PROFILE_CORE)
@Component
public class MigrationEntryGitLabToLocalVC extends ProgrammingExerciseMigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 32;

    private static final int TIMEOUT_IN_HOURS = 48;

    private static final long ESTIMATED_TIME_PER_REPOSITORY = 2; // 2s per repository

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within " + TIMEOUT_IN_HOURS + " hours. Aborting migration.";

    private static final List<String> MIGRATABLE_PROFILES = List.of("bitbucket", "localvc");

    private final static Logger log = LoggerFactory.getLogger(MigrationEntryGitLabToLocalVC.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final UriService uriService;

    private final Environment environment;

    private final Optional<LocalVCService> localVCService;

    private final Optional<AbstractVersionControlService> sourceVersionControlService;

    private final Optional<LocalVCMigrationService> localVCMigrationService;

    public MigrationEntryGitLabToLocalVC(ProgrammingExerciseRepository programmingExerciseRepository, Environment environment,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<LocalVCService> localVCService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, Optional<AbstractVersionControlService> sourceVersionControlService, UriService uriService,
            Optional<LocalVCMigrationService> localVCMigrationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.environment = environment;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.localVCService = localVCService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.sourceVersionControlService = sourceVersionControlService;
        this.uriService = uriService;
        this.localVCMigrationService = localVCMigrationService;
    }

    @Override
    public void execute() {
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        if (!new HashSet<>(activeProfiles).containsAll(MIGRATABLE_PROFILES)) {
            log.info("Migration will be skipped and marked run because the system does not support a tech-stack that requires this migration: {}",
                    List.of(environment.getActiveProfiles()));
            return;
        }

        if (localVCMigrationService.isEmpty()) {
            log.error("Migration will be skipped and marked run because the localVCMigrationService is not available.");
            return;
        }

        if (localVCMigrationService.get().getLocalVCBasePath() == null) {
            log.error("Migration will be skipped and marked run because the local VC base path is not configured.");
            return;
        }

        if (localVCMigrationService.get().getLocalVCBaseUrl() == null || localVCMigrationService.get().getLocalVCBaseUrl().toString().equals("http://0.0.0.0")) {
            log.error("Migration will be skipped and marked run because the local VC base URL is not configured.");
            return;
        }

        if (localVCMigrationService.get().getDefaultBranch() == null) {
            log.error("Migration will be skipped and marked run because the default branch is not configured.");
            return;
        }

        var programmingExerciseCount = programmingExerciseRepository.count();
        var studentCount = programmingExerciseStudentParticipationRepository.findAllWithRepositoryUri(Pageable.unpaged()).getTotalElements();

        if (programmingExerciseCount == 0) {
            // no exercises to change, migration complete
            return;
        }

        log.info("Will migrate {} programming exercises and {} student repositories now. This might take a while", programmingExerciseCount, studentCount);

        final long totalFullBatchCount = programmingExerciseCount / BATCH_SIZE;
        final long threadCount = Math.max(1, Math.min(totalFullBatchCount, MAX_THREAD_COUNT));
        final long estimatedTimeExercise = getRestDurationInSeconds(0, programmingExerciseCount, 3, threadCount);
        final long estimatedTimeStudents = getRestDurationInSeconds(0, studentCount, 1, threadCount);

        final long estimatedTime = (estimatedTimeExercise + estimatedTimeStudents);
        log.info("Using {} threads for migration, and assuming {}s per repository, the migration should take around {}", threadCount, ESTIMATED_TIME_PER_REPOSITORY,
                TimeLogUtil.formatDuration(estimatedTime));

        // Use fixed thread pool to prevent loading too many exercises into memory at once
        ExecutorService executorService = Executors.newFixedThreadPool((int) threadCount);

        /*
         * migrate the solution participations first, then the template participations, then the student participations
         */
        AtomicInteger solutionCounter = new AtomicInteger(0);
        final var totalNumberOfSolutions = solutionProgrammingExerciseParticipationRepository.count();
        log.info("Found {} solution participations to migrate.", totalNumberOfSolutions);
        for (int currentPageStart = 0; currentPageStart < totalNumberOfSolutions; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            var solutionParticipationPage = solutionProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Will migrate {} solution participations in batch.", solutionParticipationPage.getNumberOfElements());
            executorService.submit(() -> {
                migrateSolutions(solutionParticipationPage.toList());
                solutionCounter.addAndGet(solutionParticipationPage.getNumberOfElements());
                logProgress(solutionCounter.get(), totalNumberOfSolutions, threadCount, 2, "solution");
            });
        }

        log.info("Submitted all solution participations to thread pool for migration.");
        /*
         * migrate the template participations
         */
        AtomicInteger templateCounter = new AtomicInteger(0);
        var templateCount = templateProgrammingExerciseParticipationRepository.count();
        log.info("Found {} template participations to migrate", templateCount);
        for (int currentPageStart = 0; currentPageStart < templateCount; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            var templateParticipationPage = templateProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Will migrate {} template programming exercises in batch.", templateParticipationPage.getNumberOfElements());
            executorService.submit(() -> {
                migrateTemplates(templateParticipationPage.toList());
                templateCounter.addAndGet(templateParticipationPage.getNumberOfElements());
                logProgress(templateCounter.get(), templateCount, threadCount, 1, "template");
            });
        }

        log.info("Submitted all template participations to thread pool for migration.");
        /*
         * migrate the student participations
         */
        AtomicInteger studentCounter = new AtomicInteger(0);
        log.info("Found {} student programming exercise participations with build plans to migrate.", studentCount);
        for (int currentPageStart = 0; currentPageStart < studentCount; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            Page<ProgrammingExerciseStudentParticipation> studentParticipationPage = programmingExerciseStudentParticipationRepository.findAllWithRepositoryUri(pageable);
            log.info("Will migrate {} student programming exercise participations in batch.", studentParticipationPage.getNumberOfElements());
            executorService.submit(() -> {
                migrateStudents(studentParticipationPage.toList());
                studentCounter.addAndGet(studentParticipationPage.getNumberOfElements());
                logProgress(studentCounter.get(), studentCount, threadCount, 1, "student");
            });
        }

        log.info("Submitted all student participations to thread pool for migration.");

        shutdown(executorService, TIMEOUT_IN_HOURS, ERROR_MESSAGE);
        log.info("Finished migrating programming exercises and student participations");
        evaluateErrorList(programmingExerciseRepository);
    }

    private void logProgress(long doneCount, long totalCount, long threadCount, long reposPerEntry, String migrationType) {
        final double percentage = ((double) doneCount / totalCount) * 100;
        log.info("Migrated {}/{} {} participations ({}%)", doneCount, totalCount, migrationType, String.format("%.2f", percentage));
        log.info("Estimated time remaining: {} for {} repositories", TimeLogUtil.formatDuration(getRestDurationInSeconds(doneCount, totalCount, reposPerEntry, threadCount)),
                migrationType);
    }

    private long getRestDurationInSeconds(final long done, final long total, final long reposPerEntry, final long threads) {
        final long stillTodo = total - done;
        final long timePerEntry = ESTIMATED_TIME_PER_REPOSITORY * reposPerEntry;
        return (stillTodo * timePerEntry) / threads;
    }

    private String migrateTestRepo(ProgrammingExercise programmingExercise) throws URISyntaxException {
        return cloneRepositoryFromSourceVCSAndMoveToLocalVCS(programmingExercise, programmingExercise.getTestRepositoryUri(), programmingExercise.getBranch());
    }

    /**
     * Migrate auxiliary repositories of the given programming exercise.
     *
     * @param solutionParticipation the solution participation to migrate the auxiliary repositories for
     * @param programmingExercise   the programming exercise to migrate the auxiliary repositories for
     * @param oldBranch             the old branch of the programming exercise
     */
    private void migrateAuxiliaryRepositories(SolutionProgrammingExerciseParticipation solutionParticipation, ProgrammingExercise programmingExercise, String oldBranch) {
        for (var repo : getAuxiliaryRepositories(programmingExercise.getId())) {
            try {
                if (repo.getRepositoryUri() == null) {
                    log.error("Repository URI is null for auxiliary repository with id {}, cant migrate", repo.getId());
                    continue;
                }
                var url = cloneRepositoryFromSourceVCSAndMoveToLocalVCS(programmingExercise, repo.getRepositoryUri(), oldBranch);
                if (url == null) {
                    errorList.add(solutionParticipation);
                    log.error("Failed to migrate auxiliary repository for programming exercise with id {}, keeping the url in the database", programmingExercise.getId());
                }
                else {
                    log.debug("Migrated auxiliary repository with id {} to {}", repo.getId(), url);
                }
                repo.setRepositoryUri(url);
                auxiliaryRepositoryRepository.save(repo);
            }
            catch (Exception e) {
                log.error("Failed to migrate auxiliary repository with id {}", repo.getId(), e);
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
        return auxiliaryRepositoryRepository.findByExerciseId(exerciseId);
    }

    /**
     * Migrate the solution participations. Also Migrates the test and aux repository of the programming exercise since we have it
     * in the solution participation already loaded from the database.
     *
     * @param solutionParticipations the solution participations to migrate
     */
    private void migrateSolutions(List<SolutionProgrammingExerciseParticipation> solutionParticipations) {
        if (localVCMigrationService.isEmpty()) {
            log.error("Failed to migrate solution participations because the LocalVC migration service is not available.");
            return;
        }
        for (var solutionParticipation : solutionParticipations) {
            try {
                if (solutionParticipation.getRepositoryUri() == null) {
                    log.error("Repository URI is null for solution participation with id {}, cant migrate", solutionParticipation.getId());
                    errorList.add(solutionParticipation);
                    continue;
                }
                var programmingExercise = solutionParticipation.getProgrammingExercise();
                var url = cloneRepositoryFromSourceVCSAndMoveToLocalVCS(solutionParticipation.getProgrammingExercise(), solutionParticipation.getRepositoryUri(),
                        programmingExercise.getBranch());
                if (url == null) {
                    log.error("Failed to migrate solution repository for solution participation with id {}, keeping the url in the database", solutionParticipation.getId());
                    errorList.add(solutionParticipation);
                }
                else {
                    log.debug("Migrated solution participation with id {} to {}", solutionParticipation.getId(), url);
                }
                solutionParticipation.setRepositoryUri(url);
                solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
                url = migrateTestRepo(solutionParticipation.getProgrammingExercise());
                var oldBranch = programmingExercise.getBranch();
                if (url == null) {
                    log.error("Failed to migrate test repository for solution participation with id {}, keeping the url in the database", solutionParticipation.getId());
                    errorList.add(solutionParticipation);
                }
                else {
                    log.debug("Migrated test repository for solution participation with id {} to {}", solutionParticipation.getId(), url);
                    if (!localVCMigrationService.get().getDefaultBranch().equals(programmingExercise.getBranch())) {
                        programmingExercise.setBranch(localVCMigrationService.get().getDefaultBranch());
                        log.debug("Changed branch of programming exercise with id {} to {}", programmingExercise.getId(), programmingExercise.getBranch());
                    }
                }
                programmingExercise.setTestRepositoryUri(url);
                programmingExerciseRepository.save(programmingExercise);
                migrateAuxiliaryRepositories(solutionParticipation, programmingExercise, oldBranch);
            }
            catch (Exception e) {
                log.error("Failed to migrate solution participation with id {}", solutionParticipation.getId(), e);
                errorList.add(solutionParticipation);
            }
        }
    }

    /**
     * Migrate the template participations.
     *
     * @param templateParticipations list of template participations to migrate
     */
    private void migrateTemplates(List<TemplateProgrammingExerciseParticipation> templateParticipations) {
        for (var templateParticipation : templateParticipations) {
            try {
                if (templateParticipation.getRepositoryUri() == null) {
                    log.error("Repository URI is null for template participation with id {}, cant migrate", templateParticipation.getId());
                    errorList.add(templateParticipation);
                    continue;
                }
                var url = cloneRepositoryFromSourceVCSAndMoveToLocalVCS(templateParticipation.getProgrammingExercise(), templateParticipation.getRepositoryUri(),
                        templateParticipation.getProgrammingExercise().getBranch());
                if (url == null) {
                    log.error("Failed to migrate template repository for template participation with id {}, keeping the url in the database", templateParticipation.getId());
                    errorList.add(templateParticipation);
                }
                else {
                    log.debug("Migrated template participation with id {} to {}", templateParticipation.getId(), url);
                }
                templateParticipation.setRepositoryUri(url);
                templateProgrammingExerciseParticipationRepository.save(templateParticipation);
            }
            catch (Exception e) {
                log.error("Failed to migrate template participation with id {}", templateParticipation.getId(), e);
                errorList.add(templateParticipation);
            }
        }
    }

    /**
     * Migrate the student participations. This is the most time-consuming part of the migration as we have
     * to clone the repository for each student.
     *
     * @param participations list of student participations to migrate
     */
    private void migrateStudents(List<ProgrammingExerciseStudentParticipation> participations) {
        if (localVCMigrationService.isEmpty()) {
            log.error("Failed to migrate student participations because the LocalVC migration service is not available.");
            return;
        }
        for (var participation : participations) {
            try {
                if (participation.getRepositoryUri() == null) {
                    log.error("Repository URI is null for student participation with id {}, cant migrate", participation.getId());
                    errorList.add(participation);
                    continue;
                }
                var url = cloneRepositoryFromSourceVCSAndMoveToLocalVCS(participation.getProgrammingExercise(), participation.getRepositoryUri(), participation.getBranch());
                if (url == null) {
                    log.error("Failed to migrate student repository for student participation with id {}, keeping the url in the database", participation.getId());
                    errorList.add(participation);
                }
                else {
                    log.debug("Migrated student participation with id {} to {}", participation.getId(), url);
                    if (participation.getBranch() != null) {
                        participation.setBranch(localVCMigrationService.get().getDefaultBranch());
                        log.debug("Changed branch of student participation with id {} to {}", participation.getId(), participation.getBranch());
                    }
                }
                participation.setRepositoryUri(url);
                programmingExerciseStudentParticipationRepository.save(participation);
            }
            catch (Exception e) {
                log.error("Failed to migrate student participation with id {}", participation.getId(), e);
                errorList.add(participation);
            }
        }
    }

    /**
     * Clones the repository from the source VCS and moves it to the local VCS.
     * This is done by cloning the repository from the source VCS and then creating a bare repository in the local VCS.
     *
     * @param exercise      the programming exercise
     * @param repositoryUri the repository URI
     * @param oldBranch     the old branch of the programming exercise
     * @return the URI of the repository in the local VCS
     * @throws URISyntaxException if the repository URL is invalid
     */
    private String cloneRepositoryFromSourceVCSAndMoveToLocalVCS(ProgrammingExercise exercise, String repositoryUri, String oldBranch) throws URISyntaxException {
        if (localVCService.isEmpty() || sourceVersionControlService.isEmpty() || localVCMigrationService.isEmpty()) {
            log.error("Failed to clone repository from source VCS: {}", repositoryUri);
            if (localVCService.isEmpty()) {
                log.error("Local VC service is not available");
            }
            if (sourceVersionControlService.isEmpty()) {
                log.error("The source VCS service is not available");
            }
            if (localVCMigrationService.isEmpty()) {
                log.error("LocalVCMigrationService is not available");
            }
            return null;
        }
        // repo is already migrated -> return
        if (repositoryUri.startsWith(localVCMigrationService.get().getLocalVCBaseUrl().toString())) {
            log.info("Repository {} is already in local VC", repositoryUri);
            return repositoryUri;
        }
        // check if the repo exists in the source VCS, if not -> return
        if (!sourceVersionControlService.get().repositoryUriIsValid(new VcsRepositoryUri(repositoryUri))) {
            log.info("Repository {} is not available in the source VCS, removing the reference in the database", repositoryUri);
            return null;
        }
        try {
            var repositoryName = uriService.getRepositorySlugFromRepositoryUriString(repositoryUri);
            var projectKey = exercise.getProjectKey();

            localVCService.get().createProjectForExercise(exercise);
            log.info("Cloning repository {} from the source VCS and moving it to local VCS", repositoryUri);
            copyRepoToLocalVC(projectKey, repositoryName, repositoryUri, oldBranch);
            log.info("Successfully cloned repository {} from the source VCS and moved it to local VCS", repositoryUri);
            var uri = new LocalVCRepositoryUri(projectKey, repositoryName, localVCMigrationService.get().getLocalVCBaseUrl());
            return uri.toString();
        }
        catch (LocalVCInternalException e) {
            /*
             * By returning null here, we indicate that the repository does not exist anymore
             */
            log.error("Failed to clone repository from the source VCS: {}, the repository is unavailable.", repositoryUri, e);
            return null;
        }
    }

    /**
     * Clones the repository from the old origin and creates a bare repository in the local VCS, just as a normal local VC repository would be created
     * by Artemis.
     *
     * @param projectKey     the project key
     * @param repositorySlug the repository slug
     * @param oldOrigin      the old origin of the repository
     */
    private void copyRepoToLocalVC(String projectKey, String repositorySlug, String oldOrigin, String branch) {
        if (localVCMigrationService.isEmpty()) {
            log.error("Failed to clone repository from the source VCS: {}", repositorySlug);
            log.error("localVCMigrationService is not available");
            return;
        }
        LocalVCRepositoryUri localVCRepositoryUri = new LocalVCRepositoryUri(projectKey, repositorySlug, localVCMigrationService.get().getLocalVCBaseUrl());

        Path repositoryPath = localVCRepositoryUri.getLocalRepositoryPath(localVCMigrationService.get().getLocalVCBasePath());

        try {
            Files.createDirectories(repositoryPath);
            log.debug("Created local git repository folder {}", repositoryPath);

            // Create a bare local repository with JGit.
            try (Git git = Git.cloneRepository().setBranch(branch).setDirectory(repositoryPath.toFile()).setBare(true).setURI(oldOrigin).call()) {
                if (!git.getRepository().getBranch().equals(localVCMigrationService.get().getDefaultBranch())) {
                    // Rename the default branch to the configured default branch. Old exercises might have a different default branch.
                    git.branchRename().setNewName(localVCMigrationService.get().getDefaultBranch()).call();
                    log.debug("Renamed default branch of local git repository {} to {}", repositorySlug, localVCMigrationService.get().getDefaultBranch());
                }
            }
            catch (Exception e) {
                log.error("Failed to clone repository from source VCS: {}", repositorySlug, e);
                throw new LocalVCInternalException("Error while cloning repository from source VCS.", e);
            }

            log.debug("Created local git repository {} in directory {}", repositorySlug, repositoryPath);
        }
        catch (IOException e) {
            log.error("Could not create local git repo {} at location {}", repositorySlug, repositoryPath, e);
            throw new LocalVCInternalException("Error while creating local git project.", e);
        }
    }

    @Override
    public String author() {
        return "reschandreas";
    }

    @Override
    public String date() {
        return "20240103_143700";
    }
}
