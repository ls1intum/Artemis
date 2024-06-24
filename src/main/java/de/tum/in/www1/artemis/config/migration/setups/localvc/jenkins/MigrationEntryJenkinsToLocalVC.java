package de.tum.in.www1.artemis.config.migration.setups.localvc.jenkins;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.setups.localvc.LocalVCMigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.jenkins.build_plan.JenkinsBuildPlanService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

/**
 * Migration of Jenkins Build plans when the repositories will move to LocalVC.
 */
@Component
@Profile("jenkins")
public class MigrationEntryJenkinsToLocalVC extends LocalVCMigrationEntry {

    private static final String ERROR_MESSAGE = "Failed to migrate programming exercises within %d hours. Aborting migration.";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final UriService uriService;

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    public MigrationEntryJenkinsToLocalVC(ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, UriService uriService,
            JenkinsBuildPlanService jenkinsBuildPlanService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.uriService = uriService;
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
    }

    /**
     * Executes this migration entry.
     *
     * @return False if there is a general configuration error, which blocks the whole execution
     *         and true otherwise.
     */
    public boolean execute() {
        if (localVCBaseUrl == null) {
            log.error("Migration failed because the local VC base URL is not configured.");
            return false;
        }

        var programmingExerciseCount = programmingExerciseRepository.count();
        var studentCount = programmingExerciseStudentParticipationRepository.findAllWithRepositoryUri(Pageable.unpaged()).getTotalElements();

        if (programmingExerciseCount == 0) {
            log.info("No programming exercises to change");
            return true;
        }

        log.info("Will migrate {} programming exercises and {} student repositories now. This might take a while", programmingExerciseCount, studentCount);

        final long totalFullBatchCount = programmingExerciseCount / batchSize;
        final long threadCount = Math.max(1, Math.min(totalFullBatchCount, maxThreadCount));
        final long estimatedTimeExercise = getRestDurationInSeconds(0, programmingExerciseCount, 3, threadCount);
        final long estimatedTimeStudents = getRestDurationInSeconds(0, studentCount, 1, threadCount);

        final long estimatedTime = (estimatedTimeExercise + estimatedTimeStudents);
        log.info("Using {} threads for migration, and assuming {}s per repository, the migration should take around {}", threadCount, estimatedTimePerRepository,
                TimeLogUtil.formatDuration(estimatedTime));

        // Use fixed thread pool to prevent loading too many exercises into memory at once
        ExecutorService executorService = Executors.newFixedThreadPool((int) threadCount);

        // TODO migrate all kinds of repos
        /*
         * migrate the student participations
         */
        AtomicInteger studentCounter = new AtomicInteger(0);
        log.info("Found {} student programming exercise participations with build plans to migrate.", studentCount);
        for (int currentPageStart = 0; currentPageStart < studentCount; currentPageStart += batchSize) {
            Pageable pageable = PageRequest.of(currentPageStart / batchSize, batchSize);
            Page<ProgrammingExerciseStudentParticipation> studentParticipationPage = programmingExerciseStudentParticipationRepository.findAllWithRepositoryUri(pageable);
            log.info("Will migrate {} student programming exercise participations in batch.", studentParticipationPage.getNumberOfElements());
            executorService.submit(() -> {
                migrateStudents(studentParticipationPage.toList());
                studentCounter.addAndGet(studentParticipationPage.getNumberOfElements());
                logProgress(studentCounter.get(), studentCount, threadCount, 1, "student");
            });
        }
        log.info("Submitted all student participations to thread pool for migration.");

        shutdown(executorService, timeoutInHours, ERROR_MESSAGE.formatted(timeoutInHours));
        log.info("Finished migrating programming exercises and student participations");
        evaluateErrorList(programmingExerciseRepository);
        return true;
    }

    @Override
    protected Class<?> getSubclass() {
        return MigrationEntryJenkinsToLocalVC.class;
    }

    private void migrateStudents(List<ProgrammingExerciseStudentParticipation> participations) {
        for (var participation : participations) {
            try {
                if (participation.getRepositoryUri() == null) {
                    log.error("Repository URI is null for student participation with id {}, cant migrate", participation.getId());
                    errorList.add(participation);
                    continue;
                }
                changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), participation.getRepositoryUri(), participation.getBuildPlanId());
            }
            catch (Exception e) {
                log.error("Failed to migrate student participation with id {}", participation.getId(), e);
                errorList.add(participation);
            }
        }
    }

    private void changeRepositoryUriFromSourceVCSToLocalVC(ProgrammingExercise exercise, String sourceVCSRepositoryUri, String buildPlanKey) {
        // repo is already migrated -> return
        if (sourceVCSRepositoryUri.startsWith(localVCBaseUrl.toString())) {
            log.info("Repository {} is already in local VC", sourceVCSRepositoryUri);
            return;
        }
        try {
            var repositoryName = uriService.getRepositorySlugFromRepositoryUriString(sourceVCSRepositoryUri);
            var projectKey = exercise.getProjectKey();
            var localVCRepositoryUri = new LocalVCRepositoryUri(projectKey, repositoryName, localVCBaseUrl);

            jenkinsBuildPlanService.updateBuildPlanRepositories(projectKey, buildPlanKey, localVCRepositoryUri.toString(), sourceVCSRepositoryUri);
        }
        catch (JenkinsException e) {
            log.error("Failed to adjust repository uri for the source VCS uri: {}.", sourceVCSRepositoryUri, e);
        }
    }

}
