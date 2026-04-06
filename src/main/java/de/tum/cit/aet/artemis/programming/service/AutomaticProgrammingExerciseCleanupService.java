package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class AutomaticProgrammingExerciseCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticProgrammingExerciseCleanupService.class);

    private final ProfileService profileService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GitService gitService;

    private static final int STUDENT_PARTICIPATION_CLEANUP_BATCH_SIZE = 500;

    public AutomaticProgrammingExerciseCleanupService(ProfileService profileService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            GitService gitService) {
        this.profileService = profileService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitService = gitService;
    }

    /**
     * cleans up old build plans on the continuous integration server and old local git repositories on the Artemis server at 3:00:00 am in the night in form of a repeating "cron"
     * job
     */
    @Scheduled(cron = "${artemis.scheduling.programming-exercises-cleanup-time:0 0 3 * * *}") // execute this every night at 3:00:00 am
    public void cleanup() {
        if (!profileService.isProductionActive()) {
            // only execute this on production server, i.e. when the prod profile is active
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }

        try {
            cleanupGitWorkingCopiesOnArtemisServer();
        }
        catch (Exception ex) {
            log.error("Exception occurred during cleanupGitWorkingCopiesOnArtemisServer", ex);
        }
    }

    /**
     * cleans up old local git repositories on the Artemis server
     */
    public void cleanupGitWorkingCopiesOnArtemisServer() {
        SecurityUtils.setAuthorizationObject();
        log.info("Cleanup git repositories on Artemis server");
        // we are specifically interested in exercises older than 8 weeks
        var latestDate = ZonedDateTime.now().minusWeeks(8).truncatedTo(ChronoUnit.DAYS);
        // NOTE: for now we would like to cover more cases to also cleanup older repositories
        var earliestDate = latestDate.minusYears(1).truncatedTo(ChronoUnit.DAYS);

        // Cleanup all student repos in the REPOS folder (based on the student participations) 8 weeks after the exercise due date or exam end date
        // we split the cleanup in course exercises and exam exercises to improve database query performance
        cleanStudentParticipationsRepositoriesInCourseExercises(earliestDate, latestDate);
        cleanStudentParticipationsRepositoriesInExamExercises(earliestDate, latestDate);

        // Cleanup template, tests and solution repos in the REPOS folder 8 weeks after the course or exam is over
        log.info("Search for exercises with course or exam date from {} until {}", earliestDate, latestDate);
        var programmingExercises = programmingExerciseRepository.findAllByRecentCourseEndDate(earliestDate, latestDate);
        programmingExercises.addAll(programmingExerciseRepository.findAllByRecentExamEndDate(earliestDate, latestDate));
        log.info("Found {} programming exercise to clean local template, test and solution: {}", programmingExercises.size(),
                programmingExercises.stream().map(ProgrammingExercise::getProjectKey).collect(Collectors.joining(", ")));
        if (!programmingExercises.isEmpty()) {
            for (var programmingExercise : programmingExercises) {
                gitService.deleteLocalRepository(programmingExercise.getVcsTemplateRepositoryUri());
                gitService.deleteLocalRepository(programmingExercise.getVcsSolutionRepositoryUri());
                gitService.deleteLocalRepository(programmingExercise.getVcsTestRepositoryUri());
                gitService.deleteLocalProgrammingExerciseReposFolder(programmingExercise);
            }
            log.info("Finished cleaning local template, test and solution repositories");
        }
    }

    private void cleanStudentParticipationsRepositoriesInCourseExercises(ZonedDateTime earliestDate, ZonedDateTime latestDate) {
        log.info("Search for courses exercises with due date from {} until {}", earliestDate, latestDate);
        // Get all relevant participation ids
        Pageable pageable = Pageable.ofSize(STUDENT_PARTICIPATION_CLEANUP_BATCH_SIZE);
        var start = System.currentTimeMillis();
        Page<String> repositoryUriPage = programmingExerciseStudentParticipationRepository.findRepositoryUrisByCourseExerciseDueDateBetween(earliestDate, latestDate, pageable);
        log.debug("Query to find {} student participations in course exercises took {} ms", repositoryUriPage.getSize(), System.currentTimeMillis() - start);
        log.info("Found {} student participations in courses exercises to clean local student repositories in {} batches.", repositoryUriPage.getTotalElements(),
                repositoryUriPage.getTotalPages());
        if (repositoryUriPage.getTotalElements() > 0) {
            repositoryUriPage.forEach(this::deleteLocalRepositoryByUriString);
            while (!repositoryUriPage.isLast()) {
                start = System.currentTimeMillis();
                repositoryUriPage = programmingExerciseStudentParticipationRepository.findRepositoryUrisByCourseExerciseDueDateBetween(earliestDate, latestDate,
                        repositoryUriPage.nextPageable());
                log.debug("Query to {} find student participations in course exercises took {} ms", repositoryUriPage.getSize(), System.currentTimeMillis() - start);
                repositoryUriPage.forEach(this::deleteLocalRepositoryByUriString);
            }
            log.info("Finished cleaning local student repositories in course exercises");
        }
    }

    private void cleanStudentParticipationsRepositoriesInExamExercises(ZonedDateTime earliestDate, ZonedDateTime latestDate) {
        log.info("Search for exam exercises with due date from {} until {}", earliestDate, latestDate);
        // Get all relevant participation ids
        Pageable pageable = Pageable.ofSize(STUDENT_PARTICIPATION_CLEANUP_BATCH_SIZE);
        var start = System.currentTimeMillis();
        Page<String> repositoryUriPage = programmingExerciseStudentParticipationRepository.findRepositoryUrisByExamExercisesEndDateBetween(earliestDate, latestDate, pageable);
        log.debug("Query to find {} student participations in exam exercises took {} ms", repositoryUriPage.getSize(), System.currentTimeMillis() - start);
        log.info("Found {} student participations in exam exercises to clean local student repositories in {} batches.", repositoryUriPage.getTotalElements(),
                repositoryUriPage.getTotalPages());
        if (repositoryUriPage.getTotalElements() > 0) {
            repositoryUriPage.forEach(this::deleteLocalRepositoryByUriString);
            while (!repositoryUriPage.isLast()) {
                start = System.currentTimeMillis();
                repositoryUriPage = programmingExerciseStudentParticipationRepository.findRepositoryUrisByExamExercisesEndDateBetween(earliestDate, latestDate,
                        repositoryUriPage.nextPageable());
                log.debug("Query to find {} student participations in exam exercises took {} ms", repositoryUriPage.getSize(), System.currentTimeMillis() - start);
                repositoryUriPage.forEach(this::deleteLocalRepositoryByUriString);
            }
            log.info("Finished cleaning local student repositories in exam exercises");
        }
    }

    private void deleteLocalRepositoryByUriString(String uri) {
        try {
            LocalVCRepositoryUri vcsRepositoryUrl = new LocalVCRepositoryUri(uri);
            gitService.deleteLocalRepository(vcsRepositoryUrl);
        }
        catch (LocalVCInternalException e) {
            log.error("Cannot create URI for repositoryUri: {}", uri, e);
        }
    }
}
