package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;

@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class SavedPostScheduleService {

    private static final int DAYS_UNTIL_ARCHIVED_ARE_DELETED = 100;

    private static final Logger log = LoggerFactory.getLogger(SavedPostScheduleService.class);

    private final SavedPostRepository savedPostRepository;

    public SavedPostScheduleService(SavedPostRepository savedPostRepository) {
        this.savedPostRepository = savedPostRepository;
    }

    /**
     * Cleans up all archived/completed posts that are older than specified cutoff date
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupArchivedSavedPosts() {
        ZonedDateTime cutoffDate = ZonedDateTime.now().minusDays(DAYS_UNTIL_ARCHIVED_ARE_DELETED);

        List<SavedPost> oldPosts = savedPostRepository.findByCompletedAtBefore(cutoffDate);
        if (!oldPosts.isEmpty()) {
            savedPostRepository.deleteAll(oldPosts);
            log.info("Deleted {} archived saved posts", oldPosts.size());
        }
    }

    /**
     * Cleans up all saved posts where the post entity does not exist anymore
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void cleanupOrphanedSavedPosts() {
        List<SavedPost> orphanedPosts = savedPostRepository.findOrphanedPostReferences();
        if (!orphanedPosts.isEmpty()) {
            savedPostRepository.deleteAll(orphanedPosts);
            log.info("Deleted {} orphaned post references", orphanedPosts.size());
        }
    }

    /**
     * Cleans up all saved posts where the answer post entity does not exist anymore
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOrphanedSavedAnswerPosts() {
        List<SavedPost> orphanedPosts = savedPostRepository.findOrphanedAnswerReferences();
        if (!orphanedPosts.isEmpty()) {
            savedPostRepository.deleteAll(orphanedPosts);
            log.info("Deleted {} orphaned answer post references", orphanedPosts.size());
        }
    }
}
