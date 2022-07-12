package de.tum.in.www1.artemis.service.scheduled;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;

@Service
@Profile("scheduling")
public class UserScheduleService {

    @Value("${artemis.user-management.registration.cleanup-time-minutes:60}")
    private Long removeNonActivatedUserDelayTime;

    private final Logger log = LoggerFactory.getLogger(UserScheduleService.class);

    private final UserRepository userRepository;

    private final Optional<VcsUserManagementService> optionalVcsUserManagementService;

    private final CacheManager cacheManager;

    private final ScheduledExecutorService scheduler;

    // Used for tracking and canceling the non-activated accounts that will be cleaned up.
    // The key of the map is the user id.
    private final Map<Long, ScheduledFuture<?>> nonActivatedAccountsFutures = new ConcurrentHashMap<>();

    public UserScheduleService(UserRepository userRepository, Optional<VcsUserManagementService> optionalVcsUserManagementService, CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.optionalVcsUserManagementService = optionalVcsUserManagementService;
        this.cacheManager = cacheManager;
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

        ScheduledFuture<?> newFuture = scheduler.schedule(() -> {
            log.info("Removing user {} because it hasn't been activated within the hour.", nonActivatedUser);
            nonActivatedAccountsFutures.remove(nonActivatedUser.getId());
            removeNonActivatedUser(nonActivatedUser);
        }, removeNonActivatedUserDelayTime, TimeUnit.MINUTES);
        nonActivatedAccountsFutures.put(nonActivatedUser.getId(), newFuture);
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
     * @param existingUser user object of an existing user
     */
    private void removeNonActivatedUser(User existingUser) {
        if (existingUser.getActivated()) {
            return;
        }
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> {
            try {
                vcsUserManagementService.deleteVcsUser(existingUser.getLogin());
            }
            catch (VersionControlException e) {
                // Ignore exception since the user should still be deleted but log it.
                log.warn("Cannot remove non-activated user {} from the VCS: ", existingUser.getLogin(), e);
            }
        });
        deleteUser(existingUser);
    }

    /**
     * Deletes the user from the repository and the cache.
     *
     * @param user user to delete
     */
    private void deleteUser(User user) {
        userRepository.delete(user);
        clearUserCaches(user);
        userRepository.flush();
    }

    /**
     * Removes the user from Spring's cache.
     *
     * @param user the user to remove from the cache
     */
    private void clearUserCaches(User user) {
        var userCache = cacheManager.getCache(User.class.getName());
        if (userCache != null) {
            userCache.evict(user.getLogin());
        }
    }
}
