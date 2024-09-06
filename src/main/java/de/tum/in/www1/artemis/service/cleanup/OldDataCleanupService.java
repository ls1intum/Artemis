package de.tum.in.www1.artemis.service.cleanup;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.in.www1.artemis.repository.cleanup.OldDataCleanUpRepository;

@Service
public class OldDataCleanupService {

    private final OldDataCleanUpRepository oldDataCleanUpRepository;

    private final CleanupJobExecutionRepository cleanupJobExecutionRepository;

    public OldDataCleanupService(OldDataCleanUpRepository oldDataCleanUpRepository, CleanupJobExecutionRepository cleanupJobExecutionRepository) {
        this.oldDataCleanUpRepository = oldDataCleanUpRepository;
        this.cleanupJobExecutionRepository = cleanupJobExecutionRepository;
    }

    public void deleteOrphans() {
        oldDataCleanUpRepository.deleteOrphans();
    }

    public void deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deletePlagiarismComparisons(deleteFrom, deleteTo);
    }

    public void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteNonRatedResults(deleteFrom, deleteTo);
    }

    public void deleteOldRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteRatedResults(deleteFrom, deleteTo);
    }

    public void deleteSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteSubmissionVersions(deleteFrom, deleteTo);
    }

    public void deleteOldFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        oldDataCleanUpRepository.deleteFeedback(deleteFrom, deleteTo);
    }
}
