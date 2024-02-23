package de.tum.in.www1.artemis.config.migration.entries;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

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
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

@Component
public class MigrationEntry20240103_143700 extends ProgrammingExerciseMigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 32;

    private static final int TIMEOUT_IN_HOURS = 48;

    private static final long ESTIMATED_TIME_PER_REPOSITORY = 2; // 2s per repository

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within " + TIMEOUT_IN_HOURS + " hours. Aborting migration.";

    private static final List<String> MIGRATABLE_PROFILES = List.of("bitbucket", "localvc");

    private final static Logger log = LoggerFactory.getLogger(MigrationEntry20240103_143700.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final UriService uriService;

    private final Environment environment;

    private final Optional<LocalVCService> localVCService;

    private final Optional<BitbucketService> bitbucketService;

    private final Optional<BitbucketLocalVCMigrationService> bitbucketLocalVCMigrationService;

    public MigrationEntry20240103_143700(ProgrammingExerciseRepository programmingExerciseRepository, Environment environment,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<LocalVCService> localVCService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, Optional<BitbucketService> bitbucketService, UriService uriService,
            Optional<BitbucketLocalVCMigrationService> bitbucketLocalVCMigrationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.environment = environment;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.localVCService = localVCService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.bitbucketService = bitbucketService;
        this.uriService = uriService;
        this.bitbucketLocalVCMigrationService = bitbucketLocalVCMigrationService;
    }

    @Override
    public void execute() {
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        if (!new HashSet<>(activeProfiles).containsAll(MIGRATABLE_PROFILES)) {
            log.info("Migration will be skipped and marked run because the system does not support a tech-stack that requires this migration: {}",
                    List.of(environment.getActiveProfiles()));
            return;
        }

        if (bitbucketLocalVCMigrationService.isEmpty()) {
            log.error("Migration will be skipped and marked run because the BitbucketLocalVCMigrationService is not available.");
            return;
        }

        if (bitbucketLocalVCMigrationService.get().getLocalVCBasePath() == null) {
            log.error("Migration will be skipped and marked run because the local VC base path is not configured.");
            return;
        }

        if (bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl() == null || bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl().toString().equals("http://0.0.0.0")) {
            log.error("Migration will be skipped and marked run because the local VC base URL is not configured.");
            return;
        }

        if (bitbucketLocalVCMigrationService.get().getDefaultBranch() == null) {
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
        log.info("Using {} threads for migration, and assuming 2s per repository, the migration should take around {}", threadCount, TimeLogUtil.formatDuration(estimatedTime));

        // Use fixed thread pool to prevent loading too many exercises into memory at once
        ExecutorService executorService = Executors.newFixedThreadPool((int) threadCount);

        /*
         * migrate the solution participations first, then the template participations, then the student participations
         */
        AtomicInteger solutionCounter = new AtomicInteger(0);
        var solutionCount = solutionProgrammingExerciseParticipationRepository.count();
        log.info("Found {} solution participations to migrate.", solutionCount);
        for (int currentPageStart = 0; currentPageStart < solutionCount; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            var solutionParticipationPage = solutionProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Will migrate {} solution participations in batch.", solutionParticipationPage.getNumberOfElements());
            var solutionParticipationsPartitions = Lists.partition(solutionParticipationPage.toList(), (int) threadCount);
            for (var solutionParticipations : solutionParticipationsPartitions) {
                executorService.submit(() -> {
                    migrateSolutions(solutionParticipations);
                    solutionCounter.addAndGet(solutionParticipationPage.getNumberOfElements());
                    logProgress(solutionCounter, solutionCount, threadCount, 2, "solution");
                });
            }
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
            var templateParticipationsPartitions = Lists.partition(templateParticipationPage.toList(), (int) threadCount);
            for (var templateParticipations : templateParticipationsPartitions) {
                executorService.submit(() -> {
                    migrateTemplates(templateParticipations);
                    templateCounter.addAndGet(templateParticipationPage.getNumberOfElements());
                    logProgress(templateCounter, templateCount, threadCount, 1, "template");
                });
            }
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
            var studentPartitionsPartitions = Lists.partition(studentParticipationPage.toList(), (int) threadCount);
            for (var studentParticipations : studentPartitionsPartitions) {
                executorService.submit(() -> {
                    migrateStudents(studentParticipations);
                    studentCounter.addAndGet(studentParticipationPage.getNumberOfElements());
                    logProgress(studentCounter, studentCount, threadCount, 1, "student");
                });
            }
        }

        log.info("Submitted all student participations to thread pool for migration.");

        shutdown(executorService, TIMEOUT_IN_HOURS, ERROR_MESSAGE);
        log.info("Finished migrating programming exercises and student participations");
        evaluateErrorList();
    }

    private void logProgress(AtomicInteger finishedCounter, long totalCount, long threadCount, long reposPerEntry, String migrationType) {
        double percentage = ((double) finishedCounter.get() / totalCount) * 100;
        log.info("Migrated {}/{} {} participations ({}%)", finishedCounter.get(), totalCount, migrationType, String.format("%.2f", percentage));
        log.info("Estimated time remaining: {} for {} repositories",
                TimeLogUtil.formatDuration(getRestDurationInSeconds(finishedCounter.get(), totalCount, reposPerEntry, threadCount)), migrationType);
    }

    private long getRestDurationInSeconds(final long done, final long total, final long reposPerEntry, final long threads) {
        final long stillTodo = total - done;
        final long timePerEntry = ESTIMATED_TIME_PER_REPOSITORY * reposPerEntry;
        return (stillTodo * timePerEntry) / threads;
    }

    private String migrateTestRepo(ProgrammingExercise programmingExercise) throws URISyntaxException {
        return cloneRepositoryFromBitbucketAndMoveToLocalVCS(programmingExercise, programmingExercise.getTestRepositoryUri());
    }

    /**
     * Migrate auxiliary repositories of the given programming exercise.
     *
     * @param programmingExercise the programming exercise to migrate the auxiliary repositories for
     */
    private void migrateAuxiliaryRepositories(ProgrammingExercise programmingExercise) {
        for (var repo : getAuxiliaryRepositories(programmingExercise.getId())) {
            try {
                if (repo.getRepositoryUri() == null) {
                    log.error("Repository URI is null for auxiliary repository with id {}, cant migrate", repo.getId());
                    continue;
                }
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(programmingExercise, repo.getRepositoryUri());
                if (url == null) {
                    log.error("Failed to migrate auxiliary repository for programming exercise with id {}, keeping the url in the database", programmingExercise.getId());
                }
                else {
                    log.debug("Migrated auxiliary repository with id {} to {}", repo.getId(), url);
                    repo.setRepositoryUri(url);
                    auxiliaryRepositoryRepository.save(repo);
                }
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
        if (bitbucketLocalVCMigrationService.isEmpty()) {
            log.error("Failed to migrate solution participations because the Bitbucket migration service is not available.");
            return;
        }
        for (var solutionParticipation : solutionParticipations) {
            try {
                if (solutionParticipation.getRepositoryUri() == null) {
                    log.error("Repository URI is null for solution participation with id {}, cant migrate", solutionParticipation.getId());
                    errorList.add(solutionParticipation);
                    continue;
                }
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(solutionParticipation.getProgrammingExercise(), solutionParticipation.getRepositoryUri());
                if (url == null) {
                    log.error("Failed to migrate solution repository for solution participation with id {}, keeping the url in the database", solutionParticipation.getId());
                }
                else {
                    log.debug("Migrated solution participation with id {} to {}", solutionParticipation.getId(), url);
                    solutionParticipation.setRepositoryUri(url);
                    solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
                }
                url = migrateTestRepo(solutionParticipation.getProgrammingExercise());
                var programmingExercise = solutionParticipation.getProgrammingExercise();
                if (url == null) {
                    log.error("Failed to migrate test repository for solution participation with id {}, keeping the url in the database", solutionParticipation.getId());
                    errorList.add(solutionParticipation);
                }
                else {
                    log.debug("Migrated test repository for solution participation with id {} to {}", solutionParticipation.getId(), url);
                    if (!bitbucketLocalVCMigrationService.get().getDefaultBranch().equals(programmingExercise.getBranch())) {
                        programmingExercise.setBranch(bitbucketLocalVCMigrationService.get().getDefaultBranch());
                        log.debug("Changed branch of programming exercise with id {} to {}", programmingExercise.getId(), programmingExercise.getBranch());
                    }
                    programmingExercise.setTestRepositoryUri(url);
                }
                programmingExerciseRepository.save(programmingExercise);
                migrateAuxiliaryRepositories(programmingExercise);
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
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(templateParticipation.getProgrammingExercise(), templateParticipation.getRepositoryUri());
                if (url == null) {
                    log.error("Failed to migrate template repository for template participation with id {}, keeping the url in the database", templateParticipation.getId());
                    errorList.add(templateParticipation);
                }
                else {
                    templateParticipation.setRepositoryUri(url);
                    log.debug("Migrated template participation with id {} to {}", templateParticipation.getId(), url);
                    templateProgrammingExerciseParticipationRepository.save(templateParticipation);
                }
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
        if (bitbucketLocalVCMigrationService.isEmpty()) {
            log.error("Failed to migrate student participations because the Bitbucket migration service is not available.");
            return;
        }
        for (var participation : participations)
            try {
                if (participation.getRepositoryUri() == null) {
                    log.error("Repository URI is null for student participation with id {}, cant migrate", participation.getId());
                    errorList.add(participation);
                    continue;
                }
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(participation.getProgrammingExercise(), participation.getRepositoryUri());
                if (url == null) {
                    log.error("Failed to migrate student repository for student participation with id {}, keeping the url in the database", participation.getId());
                    errorList.add(participation);
                }
                else {
                    log.debug("Migrated student participation with id {} to {}", participation.getId(), url);
                    participation.setRepositoryUri(url);
                    if (participation.getBranch() != null) {
                        participation.setBranch(bitbucketLocalVCMigrationService.get().getDefaultBranch());
                        log.debug("Changed branch of student participation with id {} to {}", participation.getId(), participation.getBranch());
                    }
                    programmingExerciseStudentParticipationRepository.save(participation);
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate student participation with id {}", participation.getId(), e);
                errorList.add(participation);
            }
    }

    /**
     * Clones the repository from Bitbucket and moves it to the local VCS.
     * This is done by cloning the repository from Bitbucket and then creating a bare repository in the local VCS.
     *
     * @param exercise      the programming exercise
     * @param repositoryUri the repository URI
     * @return the URI of the repository in the local VCS
     * @throws URISyntaxException if the repository URL is invalid
     */
    private String cloneRepositoryFromBitbucketAndMoveToLocalVCS(ProgrammingExercise exercise, String repositoryUri) throws URISyntaxException {
        if (localVCService.isEmpty() || bitbucketService.isEmpty() || bitbucketLocalVCMigrationService.isEmpty()) {
            log.error("Failed to clone repository from Bitbucket: {}", repositoryUri);
            return null;
        }
        if (repositoryUri.startsWith(bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl().toString())) {
            log.info("Repository {} is already in local VC", repositoryUri);
            return repositoryUri;
        }
        if (!bitbucketService.get().repositoryUriIsValid(new VcsRepositoryUri(repositoryUri))) {
            log.info("Repository {} is not available in Bitbucket, removing the reference in the database", repositoryUri);
            return null;
        }
        try {
            var repositoryName = uriService.getRepositorySlugFromRepositoryUriString(repositoryUri);
            var projectKey = exercise.getProjectKey();

            localVCService.get().createProjectForExercise(exercise);
            log.info("Cloning repository {} from Bitbucket and moving it to local VCS", repositoryUri);
            copyRepoToLocalVC(projectKey, repositoryName, repositoryUri, exercise.getBranch());
            log.info("Successfully cloned repository {} from Bitbucket and moved it to local VCS", repositoryUri);
            var uri = new LocalVCRepositoryUri(projectKey, repositoryName, bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl());
            return uri.toString();
        }
        catch (LocalVCInternalException e) {
            /*
             * By returning null here, we indicate that the repository does not exist anymore
             */
            log.error("Failed to clone repository from Bitbucket: {}, the repository is unavailable.", repositoryUri, e);
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
        if (bitbucketLocalVCMigrationService.isEmpty()) {
            log.error("Failed to clone repository from Bitbucket: {}", repositorySlug);
            return;
        }
        LocalVCRepositoryUri localVCRepositoryUri = new LocalVCRepositoryUri(projectKey, repositorySlug, bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl());

        Path repositoryPath = localVCRepositoryUri.getLocalRepositoryPath(bitbucketLocalVCMigrationService.get().getLocalVCBasePath());
        Path cachedPath = Paths.get(bitbucketLocalVCMigrationService.get().getRepoClonePath(), projectKey, repositorySlug);

        try {
            Files.createDirectories(repositoryPath);
            log.debug("Created local git repository folder {}", repositoryPath);
            var renamedBranch = false;

            // Create a bare local repository with JGit.
            Git git = Git.cloneRepository().setBranch(branch).setDirectory(repositoryPath.toFile()).setBare(true).setURI(oldOrigin).call();

            if (!git.getRepository().getBranch().equals(bitbucketLocalVCMigrationService.get().getDefaultBranch())) {
                // Rename the default branch to the configured default branch. Old exercises might have a different default branch.
                git.branchRename().setNewName(bitbucketLocalVCMigrationService.get().getDefaultBranch()).call();
                log.debug("Renamed default branch of local git repository {} to {}", repositorySlug, bitbucketLocalVCMigrationService.get().getDefaultBranch());
                renamedBranch = true;
            }
            git.close();
            // We need to clone the repo here to the local checkout directory
            // Why? because the online editor and the CI system need a checkout of the repository to work with
            // We can't use the bare repository for this, and we directly fix the branch name to the default branch
            if (renamedBranch && Files.exists(cachedPath)) {
                try (Git localGit = Git.open(cachedPath.toFile())) {
                    localGit.branchRename().setNewName(bitbucketLocalVCMigrationService.get().getDefaultBranch()).call();
                    localGit.close();
                    log.debug("Renamed local git branch of repository {} to {}", repositorySlug, bitbucketLocalVCMigrationService.get().getDefaultBranch());
                }
                catch (Exception e) {
                    log.error("Failed to open local git repository {}", cachedPath, e);
                }
            }
            log.debug("Created local git repository {} in folder {}", repositorySlug, repositoryPath);
        }
        catch (IOException | GitAPIException e) {
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
