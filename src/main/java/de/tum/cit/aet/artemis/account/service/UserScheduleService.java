package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.user.deletion.PermanentUserDeletionService;

@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class UserScheduleService {

    @Value("${artemis.user-management.registration.cleanup-time-minutes:60}")
    private Long removeNonActivatedUserDelayTime;

    private static final Logger log = LoggerFactory.getLogger(UserScheduleService.class);

    private final PermanentUserDeletionService permanentUserDeletionService;

    private final ScheduledExecutorService scheduler;

    // Used for tracking and canceling the non-activated accounts that will be cleaned up.
    // The key of the map is the user id.
    private final Map<Long, ScheduledFuture<?>> nonActivatedAccountsFutures = new ConcurrentHashMap<>();

    public UserScheduleService(PermanentUserDeletionService permanentUserDeletionService) {
        this.permanentUserDeletionService = permanentUserDeletionService;
        this.scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Schedules the removal of the non activated user if it wasn't activated
     * after a given time. If it was already scheduled, the removal schedule is reset.
     *
     * @param nonActivatedUser the non activated user
     */
    public void scheduleForRemoveNonActivatedUser(User nonActivatedUser) {
        // Check if a future exists and cancel it before creating a new one.
        ScheduledFuture<?> future = nonActivatedAccountsFutures.get(nonActivatedUser.getId());
        if (future != null) {
            future.cancel(false);
        }

        long userId = nonActivatedUser.getId();
        ScheduledFuture<?> newFuture = scheduler.schedule(() -> {
            log.info("Checking provisional user {} for removal because the activation deadline elapsed.", userId);
            nonActivatedAccountsFutures.remove(userId);
            removeNonActivatedUser(userId);
        }, removeNonActivatedUserDelayTime, TimeUnit.MINUTES);
        nonActivatedAccountsFutures.put(userId, newFuture);
    }

    /**
     * Cancels the removal of a non activated user.
     *
     * @param user The non activated user
     */
    public void cancelScheduleRemoveNonActivatedUser(User user) {
        ScheduledFuture<?> future = nonActivatedAccountsFutures.get(user.getId());
        if (future != null) {
            future.cancel(false);
            nonActivatedAccountsFutures.remove(user.getId());
        }
    }

    /**
     * Remove non activated user.
     *
     * @param userId id of the user to reload and check
     */
    private void removeNonActivatedUser(long userId) {
        permanentUserDeletionService.deleteProvisional(userId);
    }
}
