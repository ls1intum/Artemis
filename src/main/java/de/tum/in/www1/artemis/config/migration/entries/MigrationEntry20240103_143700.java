package de.tum.in.www1.artemis.config.migration.entries;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
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
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCService;

@Component
public class MigrationEntry20240103_143700 extends MigrationEntry {

    private static final int BATCH_SIZE = 100;

    private static final int MAX_THREAD_COUNT = 10;

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within nine hours. Aborting migration.";

    private static final int TIMEOUT_IN_HOURS = 9;

    private static final List<String> MIGRATABLE_PROFILES = List.of("bitbucket", "localvc");

    private final Logger log = LoggerFactory.getLogger(MigrationEntry20240103_143700.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final Optional<LocalVCService> localVCService;

    private final Optional<BambooMigrationService> ciMigrationService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final Optional<BitbucketService> bitbucketService;

    private final UriService uriService = new UriService();

    private final Environment environment;

    private final GitService gitService;

    private final CopyOnWriteArrayList<ProgrammingExerciseParticipation> errorList = new CopyOnWriteArrayList<>();

    private final Optional<BitbucketLocalVCMigrationService> bitbucketLocalVCMigrationService;

    public MigrationEntry20240103_143700(ProgrammingExerciseRepository programmingExerciseRepository, Optional<BambooMigrationService> ciMigrationService, Environment environment,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<LocalVCService> localVCService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, GitService gitService, Optional<BitbucketService> bitbucketService,
            Optional<BitbucketLocalVCMigrationService> bitbucketLocalVCMigrationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.environment = environment;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.localVCService = localVCService;
        this.ciMigrationService = ciMigrationService;
        this.gitService = gitService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.bitbucketService = bitbucketService;
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

        if (bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl() == null) {
            log.error("Migration will be skipped and marked run because the local VC base URL is not configured.");
            return;
        }

        if (bitbucketLocalVCMigrationService.get().getDefaultBranch() == null) {
            log.error("Migration will be skipped and marked run because the default branch is not configured.");
            return;
        }

        var programmingExerciseCount = programmingExerciseRepository.count();
        var studentCount = ciMigrationService.orElseThrow().getPageableStudentParticipations(programmingExerciseStudentParticipationRepository, Pageable.unpaged())
                .getTotalElements();

        if (programmingExerciseCount == 0) {
            // no exercises to change, migration complete
            return;
        }

        log.info("Will migrate {} programming exercises and {} student repositories now. This might take a while", programmingExerciseCount, studentCount);
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

    private String migrateTestRepo(ProgrammingExercise programmingExercise) throws GitAPIException, URISyntaxException {
        return cloneRepositoryFromBitbucketAndMoveToLocalVCS(programmingExercise, programmingExercise.getTestRepositoryUri());
    }

    private void migrateAuxiliaryRepositories(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getAuxiliaryRepositories() == null) {
            return;
        }
        for (var repo : programmingExercise.getAuxiliaryRepositories()) {
            try {
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(programmingExercise, repo.getRepositoryUri());
                repo.setRepositoryUri(url);
                auxiliaryRepositoryRepository.save(repo);
            }
            catch (Exception e) {
                log.error("Failed to migrate auxiliary repository with id {}", repo.getId(), e);
            }
        }
    }

    private void migrateSolutions(List<SolutionProgrammingExerciseParticipation> solutionParticipations) {
        for (var solutionParticipation : solutionParticipations) {
            try {
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(solutionParticipation.getProgrammingExercise(), solutionParticipation.getRepositoryUri());
                solutionParticipation.setRepositoryUri(url);
                solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
                url = migrateTestRepo(solutionParticipation.getProgrammingExercise());
                var programmingExercise = solutionParticipation.getProgrammingExercise();
                programmingExercise.setTestRepositoryUri(url);
                programmingExerciseRepository.save(programmingExercise);
                migrateAuxiliaryRepositories(programmingExercise);
            }
            catch (Exception e) {
                log.error("Failed to migrate solution participation with id {}", solutionParticipation.getId(), e);
                errorList.add(solutionParticipation);
            }
        }
    }

    private void migrateTemplates(List<TemplateProgrammingExerciseParticipation> templateParticipations) {
        for (var templateParticipation : templateParticipations) {
            try {
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(templateParticipation.getProgrammingExercise(), templateParticipation.getRepositoryUri());
                templateParticipation.setRepositoryUri(url);
                templateProgrammingExerciseParticipationRepository.save(templateParticipation);
            }
            catch (Exception e) {
                log.error("Failed to migrate template participation with id {}", templateParticipation.getId(), e);
                errorList.add(templateParticipation);
            }
        }
    }

    private void migrateStudents(List<ProgrammingExerciseStudentParticipation> participations) {
        for (var participation : participations)
            try {
                var url = cloneRepositoryFromBitbucketAndMoveToLocalVCS(participation.getProgrammingExercise(), participation.getRepositoryUri());
                participation.setRepositoryUri(url);
                programmingExerciseStudentParticipationRepository.save(participation);
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

    private String cloneRepositoryFromBitbucketAndMoveToLocalVCS(ProgrammingExercise exercise, String repositoryUrl) throws GitAPIException, URISyntaxException {
        if (localVCService.isEmpty() || bitbucketService.isEmpty() || bitbucketLocalVCMigrationService.isEmpty()) {
            log.error("Failed to clone repository from Bitbucket: {}", repositoryUrl);
            return null;
        }
        var repositoryName = uriService.getRepositorySlugFromRepositoryUriString(repositoryUrl);
        var projectKey = exercise.getProjectKey();
        Repository oldRepository = gitService.getOrCheckoutRepository(new VcsRepositoryUri(repositoryUrl), true, bitbucketLocalVCMigrationService.get().getDefaultBranch());
        if (oldRepository == null) {
            log.error("Failed to clone repository from Bitbucket: {}", repositoryUrl);
            return null;
        }
        localVCService.get().createProjectForExercise(exercise);
        cloneRepo(projectKey, repositoryName, repositoryUrl);
        var url = new LocalVCRepositoryUri(projectKey, repositoryName, bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl());
        return url.toString();
    }

    private void cloneRepo(String projectKey, String repositorySlug, String oldOrigin) {
        LocalVCRepositoryUri localVCRepositoryUri = new LocalVCRepositoryUri(projectKey, repositorySlug, bitbucketLocalVCMigrationService.get().getLocalVCBaseUrl());

        Path remoteDirPath = localVCRepositoryUri.getLocalRepositoryPath(bitbucketLocalVCMigrationService.get().getLocalVCBasePath());

        try {
            Files.createDirectories(remoteDirPath);

            // Create a bare local repository with JGit.
            Git git = Git.cloneRepository().setDirectory(remoteDirPath.toFile()).setBare(true).setURI(oldOrigin)
                    .setBranch(bitbucketLocalVCMigrationService.get().getDefaultBranch()).call();

            git.close();
            log.debug("Created local git repository {} in folder {}", repositorySlug, remoteDirPath);
        }
        catch (GitAPIException | IOException e) {
            log.error("Could not create local git repo {} at location {}", repositorySlug, remoteDirPath, e);
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
