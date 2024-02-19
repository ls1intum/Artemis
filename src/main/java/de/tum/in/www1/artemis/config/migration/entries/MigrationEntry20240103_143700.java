package de.tum.in.www1.artemis.config.migration.entries;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.participation.ParticipationInterface;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
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
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

@Component
public class MigrationEntry20240103_143700 extends MigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 32;

    private static final int TIMEOUT_IN_HOURS = 48;

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

    private final GitService gitService;

    private final CopyOnWriteArrayList<ProgrammingExerciseParticipation> errorList = new CopyOnWriteArrayList<>();

    public MigrationEntry20240103_143700(ProgrammingExerciseRepository programmingExerciseRepository, Environment environment,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<LocalVCService> localVCService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, Optional<BitbucketService> bitbucketService, UriService uriService,
            Optional<BitbucketLocalVCMigrationService> bitbucketLocalVCMigrationService, GitService gitService) {
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
        this.gitService = gitService;
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
        final int threadCount = (int) Math.max(1, Math.min(totalFullBatchCount, MAX_THREAD_COUNT));
        final long estimatedTimeExercise = getRestDurationInSeconds(0, programmingExerciseCount, 3, threadCount);
        final long estimatedTimeStudents = getRestDurationInSeconds(0, studentCount, 1, threadCount);

        final long estimatedTime = (estimatedTimeExercise + estimatedTimeStudents);
        log.info("Using {} threads for migration, and assuming 2s per repository, the migration should take around {}", MAX_THREAD_COUNT,
                TimeLogUtil.formatDuration(estimatedTime));
        // Number of full batches. The last batch might be smaller

        // Use fixed thread pool to prevent loading too many exercises into memory at once
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

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
            var solutionParticipationsPartitions = Lists.partition(solutionParticipationPage.toList(), threadCount);
            for (var solutionParticipations : solutionParticipationsPartitions) {
                executorService.submit(() -> migrateSolutions(solutionParticipations));
            }
            solutionCounter.addAndGet(solutionParticipationPage.getNumberOfElements());
            log.info("Migrated {}/{} solution participations", solutionCounter.get(), solutionCount);
            log.info("Estimated time remaining: {} for solution repositories",
                    TimeLogUtil.formatDuration(getRestDurationInSeconds(solutionCounter.get(), solutionCount, 2, threadCount)));
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
            var templateParticipationsPartitions = Lists.partition(templateParticipationPage.toList(), threadCount);
            for (var templateParticipations : templateParticipationsPartitions) {
                executorService.submit(() -> migrateTemplates(templateParticipations));
            }
            templateCounter.addAndGet(templateParticipationPage.getNumberOfElements());
            log.info("Migrated {}/{} template participations", templateCounter.get(), templateCount);
            log.info("Estimated time remaining: {} hours for template repositories",
                    TimeLogUtil.formatDuration(getRestDurationInSeconds(templateCounter.get(), templateCount, 1, threadCount)));
        }

        log.info("Submitted all template participations to thread pool for migration.");
        /*
         * migrate the student participations
         */
        AtomicInteger studentCounter = new AtomicInteger(0);
        log.info("Found {} student programming exercise participations with build plans to migrate.", studentCount);
        for (int currentPageStart = 0; currentPageStart < studentCount; currentPageStart += BATCH_SIZE) {
            Pageable pageable = PageRequest.of(currentPageStart / BATCH_SIZE, BATCH_SIZE);
            Page<ProgrammingExerciseStudentParticipation> studentParticipationPage = programmingExerciseStudentParticipationRepository.findAllWithBuildPlanId(pageable);
            log.info("Will migrate {} student programming exercise participations in batch.", studentParticipationPage.getNumberOfElements());
            var studentPartitionsPartitions = Lists.partition(studentParticipationPage.toList(), threadCount);
            for (var studentParticipations : studentPartitionsPartitions) {
                executorService.submit(() -> migrateStudents(studentParticipations));
            }
            studentCounter.addAndGet(studentParticipationPage.getNumberOfElements());
            log.info("Migrated {}/{} student participations", studentCounter.get(), studentCount);
            log.info("Estimated time remaining: {} hours for student repositories",
                    TimeLogUtil.formatDuration(getRestDurationInSeconds(studentCounter.get(), studentCount, 1, threadCount)));
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

    private long getRestDurationInSeconds(final int done, final long total, final int reposPerEntry, final int threads) {
        final long ESTIMATED_TIME_PER_REPOSITORY = 2; // 2s per repository
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
     * Migrate the solution participations. Also Migrates the test repository of the programming exercise since we have it
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
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(solutionParticipation.getProgrammingExercise(), solutionParticipation.getRepositoryUri());
                if (url == null) {
                    log.error("Failed to migrate solution repository for solution participation with id {}, keeping the url in the database", solutionParticipation.getId());
                }
                else {
                    log.debug("Migrated solution participation with id {} to {}", solutionParticipation.getId(), url);
                    solutionParticipation.setRepositoryUri(url);
                    solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
                    errorList.add(solutionParticipation);
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
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(participation.getProgrammingExercise(), participation.getRepositoryUri());
                if (url == null) {
                    log.error("Failed to migrate student repository for student participation with id {}, keeping the url in the database", participation.getId());
                    errorList.add(participation);
                }
                else {
                    log.debug("Migrated student participation with id {} to {}", participation.getId(), url);
                    participation.setRepositoryUri(url);
                    programmingExerciseStudentParticipationRepository.save(participation);
                }
                if (url != null && !bitbucketLocalVCMigrationService.get().getDefaultBranch().equals(participation.getBranch())) {
                    participation.setBranch(bitbucketLocalVCMigrationService.get().getDefaultBranch());
                    log.debug("Changed branch of student participation with id {} to {}", participation.getId(), participation.getBranch());
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate student participation with id {}", participation.getId(), e);
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
                + "the migration with author: {} and date_string: {} and then restarting Artemis.", author(), date());
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
            log.debug("Cloning repository {} from Bitbucket and moving it to local VCS", repositoryUri);
            copyRepoToLocalVC(projectKey, repositoryName, repositoryUri, exercise.getBranch());
            log.debug("Successfully cloned repository {} from Bitbucket and moved it to local VCS", repositoryUri);
            var uri = new LocalVCRepositoryUri(projectKey, repositoryName, bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl());
            return uri.toString();
        }
        catch (LocalVCInternalException e) {
            /*
             * By returning null here, we indicate that the repository does not exist anymore
             */
            log.error("Failed to clone repository from Bitbucket: {}, the repository is unavailable.", repositoryUri);
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

        try {
            Files.createDirectories(repositoryPath);
            log.debug("Created local git repository folder {}", repositoryPath);

            // Create a bare local repository with JGit.
            Git git = Git.cloneRepository().setBranch(branch).setDirectory(repositoryPath.toFile()).setBare(true).setURI(oldOrigin).call();

            if (!git.getRepository().getBranch().equals(bitbucketLocalVCMigrationService.get().getDefaultBranch())) {
                // Rename the default branch to the configured default branch.
                git.branchRename().setNewName(bitbucketLocalVCMigrationService.get().getDefaultBranch()).call();
                log.debug("Renamed default branch of local git repository {} to {}", repositorySlug, bitbucketLocalVCMigrationService.get().getDefaultBranch());
            }
            git.close();
            try {
                // We need to clone the repo here to the local checkout directory
                if (gitService.getOrCheckoutRepository(new VcsRepositoryUri(oldOrigin), true) != null) {
                    log.debug("Cloned local git repository {} to {}", repositorySlug, repositoryPath);
                }
                else {
                    log.error("Failed to clone local git repository {} to {}", repositorySlug, repositoryPath);
                }
            }
            catch (Exception e) {
                log.error("Failed to clone local git repository {} to {}", repositorySlug, repositoryPath, e);
            }
            log.debug("Created local git repository {} in folder {}", repositorySlug, repositoryPath);
        }
        catch (GitAPIException | IOException e) {
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
