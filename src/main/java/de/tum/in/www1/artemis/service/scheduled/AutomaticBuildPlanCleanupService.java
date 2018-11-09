package de.tum.in.www1.artemis.service.scheduled;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static java.time.ZonedDateTime.now;

@Service
public class AutomaticBuildPlanCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticBuildPlanCleanupService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;
    private final ParticipationRepository participationRepository;

    public AutomaticBuildPlanCleanupService(ProgrammingExerciseRepository programmingExerciseRepository,
                                            ParticipationRepository participationRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationRepository = participationRepository;
    }

    //TODO: before we deploy this to production, we need to make sure the scheduler is deactivated
    private static ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    static {
        threadPoolTaskScheduler.setThreadNamePrefix("AutomaticBuildPlanCleanupService");
        threadPoolTaskScheduler.setPoolSize(1);
        threadPoolTaskScheduler.initialize();
    }

    private ScheduledFuture scheduledFuture;

    /**
     * start scheduler
     */
    public void startSchedule(long delayInMillis) {
        log.info("AutomaticBuildPlanCleanupService was started to run repeatedly with {} second gaps.", delayInMillis / 1000.0);
        scheduledFuture = threadPoolTaskScheduler.scheduleWithFixedDelay(this::cleanupBuildPlans, delayInMillis);
    }

    /**
     * stop scheduler (doesn't interrupt if running)
     */
    public void stopSchedule() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    @Scheduled(cron="30 2 * * * *") //execute this every night at 2:30 am
    @Transactional
    public void cleanupBuildPlans() {
        long start = System.currentTimeMillis();
        log.info("Find build plans for potential cleanup");

        int countRecentBuildPlans = 0;
        int countExpiredBuildPlans = 0;
        int countExpiredSuccessfulBuildPlans = 0;
        int countNullResults = 0;
        start = System.currentTimeMillis();
        List<Participation> participations = participationRepository.findAllExpiredWithBuildPlanId();
        for (Participation participation : participations) {
            Result result = participation.findLatestResult();
            if (result == null) {
                countNullResults++;
            }
            else if (result.getCompletionDate().plusDays(7).isBefore(now())) {
                countExpiredBuildPlans++;
                if (result.isSuccessful()) {
                    countExpiredSuccessfulBuildPlans++;
                }
            }
            else {
                countRecentBuildPlans++;
            }
        }
        log.info("Found " + participations.size() + " potential build plans to clean up after " + (System.currentTimeMillis() - start) + " ms execution time");
        log.info("Found " + countExpiredBuildPlans + " participations with build plans with their latest result being older than 7 days");
        log.info("Found " + countExpiredSuccessfulBuildPlans + " out of this with a successful latest result");
        log.info("Found " + countNullResults + " participations with build plans without result");
        log.info("Found " + countRecentBuildPlans + " participations with build plans with a recent latest result (i.e. not older than 7 days)");
    }
}
