package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.config.Constants.EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE;
import static de.tum.in.www1.artemis.config.Constants.EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS;
import static java.time.ZonedDateTime.now;

import java.util.*;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.service.ParticipationService;
import io.github.jhipster.config.JHipsterConstants;

@Service
public class AutomaticBuildPlanCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticBuildPlanCleanupService.class);

    private final Environment env;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ParticipationService participationService;

    public AutomaticBuildPlanCleanupService(Environment env, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ParticipationService participationService) {
        this.env = env;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.participationService = participationService;
    }

    /**
     *  Cleans up all build plans
     */
    @Scheduled(cron = "0 0 3 * * *") // execute this every night at 3:00:00 am
    public void cleanupBuildPlans() {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            // only execute this on production server, i.e. when the prod profile is active
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }

        long start = System.currentTimeMillis();
        log.info("Find build plans for potential cleanup");

        long countAfter1DayAfterBuildAndTestStudentSubmissionsAfterDueDate = 0;
        long countNoResultAfter7Days = 0;
        long countSuccessfulLatestResultAfter3Days = 0;
        long countUnsuccessfulLatestResultAfter7Days = 0;

        List<ProgrammingExerciseStudentParticipation> allParticipationsWithBuildPlanId = programmingExerciseStudentParticipationRepository.findAllWithBuildPlanId();
        Set<ProgrammingExerciseStudentParticipation> participationsWithBuildPlanToDelete = new HashSet<>();

        for (ProgrammingExerciseStudentParticipation participation : allParticipationsWithBuildPlanId) {

            if (participation.getBuildPlanId() == null) {
                // already cleaned up
                continue;
            }
            if (participation.getStudent() == null) {
                // we only want to clean up build plans of students
                continue;
            }

            if (participation.getProgrammingExercise() != null && Hibernate.isInitialized(participation.getProgrammingExercise())) {
                var programmingExercise = participation.getProgrammingExercise();

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

                if (programmingExercise.isPublishBuildPlanUrl() == Boolean.TRUE) {
                    // this was an exercise where students needed to configure the build plan, therefore we should not clean it up
                    continue;
                }
            }

            Result result = participation.findLatestResult();
            // 2nd case: delete the build plan 7 days after the participation was initialized in case there is no result
            if (result == null) {
                if (participation.getInitializationDate() != null && participation.getInitializationDate().plusDays(7).isBefore(now())) {
                    participationsWithBuildPlanToDelete.add(participation);
                    countNoResultAfter7Days++;
                }
            }
            else {
                // 3rd case: delete the build plan after 3 days in case the latest result is successful
                if (result.isSuccessful()) {
                    if (result.getCompletionDate() != null && result.getCompletionDate().plusDays(3).isBefore(now())) {
                        participationsWithBuildPlanToDelete.add(participation);
                        countSuccessfulLatestResultAfter3Days++;
                    }
                }
                // 4th case: delete the build plan after 7 days in case the latest result is NOT successful
                else {
                    if (result.getCompletionDate() != null && result.getCompletionDate().plusDays(7).isBefore(now())) {
                        participationsWithBuildPlanToDelete.add(participation);
                        countUnsuccessfulLatestResultAfter7Days++;
                    }
                }
            }
        }

        log.info("Found " + allParticipationsWithBuildPlanId.size() + " participations with build plans in " + (System.currentTimeMillis() - start) + " ms execution time");
        log.info("Found " + participationsWithBuildPlanToDelete.size() + " old build plans to delete");
        log.info("  Found " + countAfter1DayAfterBuildAndTestStudentSubmissionsAfterDueDate + " build plans at least 1 day older than 'build and test submissions after due date");
        log.info("  Found " + countNoResultAfter7Days + " build plans without results 7 days after initialization");
        log.info("  Found " + countSuccessfulLatestResultAfter3Days + " build plans with successful latest result is older than 3 days");
        log.info("  Found " + countUnsuccessfulLatestResultAfter7Days + " build plans with unsuccessful latest result is older than 7 days");

        // Limit to 2000 deletions per night
        List<ProgrammingExerciseStudentParticipation> actualParticipationsToClean = participationsWithBuildPlanToDelete.stream().limit(2000).collect(Collectors.toList());
        List<String> buildPlanIds = actualParticipationsToClean.stream().map(ProgrammingExerciseStudentParticipation::getBuildPlanId).collect(Collectors.toList());
        log.info("Build plans to cleanup: " + buildPlanIds);

        int index = 0;
        for (ProgrammingExerciseStudentParticipation participation : actualParticipationsToClean) {
            if (index > 0 && index % EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE == 0) {
                try {
                    log.info("Sleep for {}s during cleanupBuildPlans", EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS / 1000);
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
