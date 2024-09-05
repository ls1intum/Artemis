package de.tum.in.www1.artemis.service.cleanup;

import java.time.ZonedDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.cleanup.OldDataCleanUpRepository;

// TODO Dmytro: when an operation is executed, update the lastExecuted in the operations table
@Service
public class OldDataCleanupService {

    private final OldDataCleanUpRepository oldDataCleanUpRepository;

    public OldDataCleanupService(OldDataCleanUpRepository oldDataCleanUpRepository) {
        this.oldDataCleanUpRepository = oldDataCleanUpRepository;
    }

    public void deleteOrphans(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteOrphans(deleteFrom, deleteTo);
    }

    public void deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deletePlagiarismComparisons(deleteFrom, deleteTo);
    }

    public void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteNonRatedResults(deleteFrom, deleteTo);
    }

    public void deleteOldRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteOldRatedResults(deleteFrom, deleteTo);
    }

    public void deleteOldSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteOldSubmissionVersions(deleteFrom, deleteTo);
    }

    public void deleteOldFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteOldFeedback(deleteFrom, deleteTo);
    }

    @Scheduled(fixedRateString = "#{T(java.time.Duration).ofDays(182).toMillis()}", initialDelayString = "#{T(java.time.Duration).ofHours(1).toMillis()}")
    public void scheduleDataCleanup() {
        if (dataNeedsCleanup()) {
            ZonedDateTime cutoffDate = ZonedDateTime.now().minusDays(182);
            // cleanupOldData(cutoffDate);
        }
    }

    public void cleanupOldData(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        deleteOrphans(deleteFrom, deleteTo);
        deletePlagiarismComparisons(deleteFrom, deleteTo);
        deleteNonRatedResults(deleteFrom, deleteTo);
        deleteOldRatedResults(deleteFrom, deleteTo);
        deleteOldSubmissionVersions(deleteFrom, deleteTo);
        deleteOldFeedback(deleteFrom, deleteTo);
    }

    private boolean dataNeedsCleanup() {
        return true;
    }
}
