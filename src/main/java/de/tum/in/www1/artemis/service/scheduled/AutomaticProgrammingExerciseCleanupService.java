package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.config.Constants.*;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import io.github.jhipster.config.JHipsterConstants;

@Service
@Profile("scheduling")
public class AutomaticProgrammingExerciseCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticProgrammingExerciseCleanupService.class);

    private final Environment env;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ParticipationService participationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GitService gitService;

    public AutomaticProgrammingExerciseCleanupService(Environment env, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ParticipationService participationService, ProgrammingExerciseRepository programmingExerciseRepository, GitService gitService) {
        this.env = env;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.participationService = participationService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitService = gitService;
    }

    /**
     * cleans up old build plans on the continuous integration server and old local git repositories on the Artemis server at 3:00:00 am in the night in form of a repeating "cron" job
     */
    @Scheduled(cron = "0 0 3 * * *") // execute this every night at 3:00:00 am
    public void cleanup() {
        try {
            cleanupBuildPlansOnContinuousIntegrationServer();
        }
        catch (Exception ex) {
            log.error("Exception occurred during cleanupBuildPlansOnContinuousIntegrationServer " + ex.getMessage(), ex);
        }
        try {
            cleanupGitRepositoriesOnArtemisServer();
        }
        catch (Exception ex) {
            log.error("Exception occurred during cleanupGitRepositoriesOnArtemisServer " + ex.getMessage(), ex);
        }
    }

    /**
     * cleans up old local git repositories on the Artemis server
     */
    public void cleanupGitRepositoriesOnArtemisServer() {
        SecurityUtils.setAuthorizationObject();
        // we are specifically interested in one whole day 8 weeks ago
        var endDate2 = ZonedDateTime.now().minusWeeks(8).truncatedTo(ChronoUnit.DAYS);
        // NOTE: for now we would like to cover more cases to also cleanup older repositories
        var endDate1 = endDate2.minusYears(1).truncatedTo(ChronoUnit.DAYS);

        // Cleanup all student repos in the REPOS folder (based on the student participations) 8 weeks after the exercise due date
        var programmingExercises = programmingExerciseRepository.findAllWithStudentParticipationByRecentEndDate(endDate1, endDate2);
        log.info("Found " + programmingExercises.size() + " programming exercise to clean local student repositories: "
                + programmingExercises.stream().map(ProgrammingExercise::getProjectKey).collect(Collectors.joining(", ")));
        for (var programmingExercise : programmingExercises) {
            for (var studentParticipation : programmingExercise.getStudentParticipations()) {
                var programmingExerciseParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
                gitService.deleteLocalRepository(programmingExerciseParticipation.getVcsRepositoryUrl());
            }
        }

        // Cleanup template, tests and solution repos in the REPOS folder 8 weeks after the course is over
        programmingExercises = programmingExerciseRepository.findAllByRecentCourseEndDate(endDate1, endDate2);
        log.info("Found " + programmingExercises.size() + " programming exercise to clean local template, test and solution: "
                + programmingExercises.stream().map(ProgrammingExercise::getProjectKey).collect(Collectors.joining(", ")));
        for (var programmingExercise : programmingExercises) {
            gitService.deleteLocalRepository(programmingExercise.getVcsTemplateRepositoryUrl());
            gitService.deleteLocalRepository(programmingExercise.getVcsSolutionRepositoryUrl());
            gitService.deleteLocalRepository(programmingExercise.getVcsTestRepositoryUrl());
            gitService.deleteLocalProgrammingExerciseReposFolder(programmingExercise);
        }
    }

    /**
     *  Cleans up old build plans on the continuous integration server
     */
    private void cleanupBuildPlansOnContinuousIntegrationServer() {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            // only execute this on production server, i.e. when the prod profile is active
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }

        long start = System.currentTimeMillis();
        log.info("Find build plans for potential cleanup");

        long countAfter1DayAfterBuildAndTestStudentSubmissionsAfterDueDate = 0;
        long countNoResultAfter3Days = 0;
        long countSuccessfulLatestResultAfter1Days = 0;
        long countUnsuccessfulLatestResultAfter5Days = 0;

        List<ProgrammingExerciseStudentParticipation> allParticipationsWithBuildPlanId = programmingExerciseStudentParticipationRepository.findAllWithBuildPlanIdWithResults();
        Set<ProgrammingExerciseStudentParticipation> participationsWithBuildPlanToDelete = new HashSet<>();

        for (ProgrammingExerciseStudentParticipation participation : allParticipationsWithBuildPlanId) {

            if (participation.getBuildPlanId() == null) {
                // already cleaned up
                continue;
            }
            if (participation.getParticipant() == null) {
                // we only want to clean up build plans of students or teams (NOT template or solution build plans)
                continue;
            }

            if (participation.getProgrammingExercise() != null && Hibernate.isInitialized(participation.getProgrammingExercise())) {
                var programmingExercise = participation.getProgrammingExercise();

                if (programmingExercise.isExamExercise() && programmingExercise.getExerciseGroup().getExam() != null) {
                    var exam = programmingExercise.getExerciseGroup().getExam();
                    if (exam.getEndDate().plusDays(1).isAfter(now())) {
                        // we don't clean up plans that will definitely be executed in the future as part of an exam (and we have 1 day buffer time for exams)
                        continue;
                    }
                }

                if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null) {
                    if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().isAfter(now())) {
                        // we don't clean up plans that will definitely be executed in the future
                        continue;
                    }

                    // 1st case: delete the build plan 1 day after the build and test student submissions after due date, because then no builds should be executed any more
                    // and the students repos will be locked anyways.
                    if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().plusDays(1).isBefore(now())) {
                        participationsWithBuildPlanToDelete.add(participation);
                        countAfter1DayAfterBuildAndTestStudentSubmissionsAfterDueDate++;
                        continue;
                    }
                }

                if (Boolean.TRUE.equals(programmingExercise.isPublishBuildPlanUrl())) {
                    // this was an exercise where students needed to configure the build plan, therefore we should not clean it up
                    continue;
                }
            }

            Result result = participation.findLatestResult();
            // 2nd case: delete the build plan 3 days after the participation was initialized in case there is no result
            if (result == null) {
                if (participation.getInitializationDate() != null && participation.getInitializationDate().plusDays(3).isBefore(now())) {
                    participationsWithBuildPlanToDelete.add(participation);
                    countNoResultAfter3Days++;
                }
            }
            else {
                // 3rd case: delete the build plan after 1 days in case the latest result is successful
                if (result.isSuccessful()) {
                    if (result.getCompletionDate() != null && result.getCompletionDate().plusDays(1).isBefore(now())) {
                        participationsWithBuildPlanToDelete.add(participation);
                        countSuccessfulLatestResultAfter1Days++;
                    }
                }
                // 4th case: delete the build plan after 5 days in case the latest result is NOT successful
                else {
                    if (result.getCompletionDate() != null && result.getCompletionDate().plusDays(5).isBefore(now())) {
                        participationsWithBuildPlanToDelete.add(participation);
                        countUnsuccessfulLatestResultAfter5Days++;
                    }
                }
            }
        }

        log.info("Found " + allParticipationsWithBuildPlanId.size() + " participations with build plans in " + (System.currentTimeMillis() - start) + " ms execution time");
        log.info("Found " + participationsWithBuildPlanToDelete.size() + " old build plans to delete");
        log.info("  Found " + countAfter1DayAfterBuildAndTestStudentSubmissionsAfterDueDate + " build plans at least 1 day older than 'build and test submissions after due date");
        log.info("  Found " + countNoResultAfter3Days + " build plans without results 3 days after initialization");
        log.info("  Found " + countSuccessfulLatestResultAfter1Days + " build plans with successful latest result is older than 1 day");
        log.info("  Found " + countUnsuccessfulLatestResultAfter5Days + " build plans with unsuccessful latest result is older than 5 days");

        // Limit to 5000 deletions per night
        List<ProgrammingExerciseStudentParticipation> actualParticipationsToClean = participationsWithBuildPlanToDelete.stream().limit(5000).collect(Collectors.toList());
        List<String> buildPlanIds = actualParticipationsToClean.stream().map(ProgrammingExerciseStudentParticipation::getBuildPlanId).collect(Collectors.toList());
        log.info("Build plans to cleanup: " + buildPlanIds);

        int index = 0;
        for (ProgrammingExerciseStudentParticipation participation : actualParticipationsToClean) {
            if (index > 0 && index % EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE == 0) {
                try {
                    log.info("Sleep for {}s during cleanupBuildPlansOnContinuousIntegrationServer", EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS / 1000);
                    Thread.sleep(EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS);
                }
                catch (InterruptedException ex) {
                    log.error("Exception encountered when pausing before cleaning up build plans", ex);
                }
            }

            try {
                participationService.cleanupBuildPlan(participation);
            }
            catch (Exception ex) {
                log.error("Could not cleanup build plan in participation " + participation.getId(), ex);
            }

            index++;
        }
        log.info(actualParticipationsToClean.size() + " build plans have been cleaned");
    }
}
