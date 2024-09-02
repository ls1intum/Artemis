package de.tum.in.www1.artemis.service.cleanup;

import java.time.ZonedDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.cleanup.OldDataCleanUpRepository;

@Service
public class OldDataCleanupService {

    private final OldDataCleanUpRepository oldDataCleanUpRepository;

    public OldDataCleanupService(OldDataCleanUpRepository oldDataCleanUpRepository) {
        this.oldDataCleanUpRepository = oldDataCleanUpRepository;
    }

    public void cleanupOldData(ZonedDateTime keepAllLaterThan) {
        oldDataCleanUpRepository.deleteOrphans();
        oldDataCleanUpRepository.deletePlagiarismComparisons(keepAllLaterThan);
        oldDataCleanUpRepository.deleteNonRatedResults(keepAllLaterThan);
        oldDataCleanUpRepository.deleteOldRatedResults(keepAllLaterThan);
        oldDataCleanUpRepository.deleteOldSubmissionVersions(keepAllLaterThan);
        oldDataCleanUpRepository.deleteOldFeedback(keepAllLaterThan);
    }

    @Scheduled(fixedRateString = "#{T(java.time.Duration).ofDays(182).toMillis()}", initialDelayString = "#{T(java.time.Duration).ofHours(1).toMillis()}")
    public void scheduleDataCleanup() {
        if (dataNeedsCleanup()) {
            cleanupOldData(ZonedDateTime.now().minusDays(182));
        }
    }

    private boolean dataNeedsCleanup() {
        return true;
    }

}
