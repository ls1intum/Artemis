package de.tum.in.www1.artemis.service.cleanup;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.cleanup.CleanupJobExecution;
import de.tum.in.www1.artemis.domain.enumeration.CleanupJobType;
import de.tum.in.www1.artemis.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.in.www1.artemis.repository.cleanup.DataCleanupRepository;
import de.tum.in.www1.artemis.web.rest.dto.CleanupServiceExecutionRecordDTO;

@Profile(PROFILE_CORE)
@Service
public class DataCleanupService {

    private final DataCleanupRepository dataCleanUpRepository;

    private final CleanupJobExecutionRepository cleanupJobExecutionRepository;

    public DataCleanupService(DataCleanupRepository dataCleanUpRepository, CleanupJobExecutionRepository cleanupJobExecutionRepository) {
        this.dataCleanUpRepository = dataCleanUpRepository;
        this.cleanupJobExecutionRepository = cleanupJobExecutionRepository;
    }

    public CleanupServiceExecutionRecordDTO deleteOrphans() {
        dataCleanUpRepository.deleteOrphans();
        return CleanupServiceExecutionRecordDTO.of(this.createDBEntry(CleanupJobType.ORPHANS, null, null));
    }

    public CleanupServiceExecutionRecordDTO deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deletePlagiarismComparisons(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createDBEntry(CleanupJobType.PLAGIARISM_COMPARISONS, deleteFrom, deleteTo));
    }

    public CleanupServiceExecutionRecordDTO deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteNonRatedResults(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createDBEntry(CleanupJobType.NON_RATED_RESULTS, deleteFrom, deleteTo));
    }

    public CleanupServiceExecutionRecordDTO deleteRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteRatedResults(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createDBEntry(CleanupJobType.RATED_RESULTS, deleteFrom, deleteTo));
    }

    public CleanupServiceExecutionRecordDTO deleteSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteSubmissionVersions(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createDBEntry(CleanupJobType.SUBMISSION_VERSIONS, deleteFrom, deleteTo));
    }

    public CleanupServiceExecutionRecordDTO deleteFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteFeedback(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createDBEntry(CleanupJobType.FEEDBACK, deleteFrom, deleteTo));
    }

    public List<CleanupServiceExecutionRecordDTO> getLastExecutions() {
        List<CleanupServiceExecutionRecordDTO> executionRecords = new ArrayList<>();

        for (CleanupJobType jobType : CleanupJobType.values()) {
            CleanupJobExecution lastExecution = cleanupJobExecutionRepository.findTopByCleanupJobTypeOrderByDeletionTimestampDesc(jobType);
            if (lastExecution != null) {
                executionRecords.add(CleanupServiceExecutionRecordDTO.of(lastExecution));
            }
            else {
                // for jobs that have not been run yet
                executionRecords.add(new CleanupServiceExecutionRecordDTO(null, jobType.getName()));
            }
        }

        return executionRecords;
    }

    private CleanupJobExecution createDBEntry(CleanupJobType cleanupJobType, ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        var entry = new CleanupJobExecution();
        entry.setCleanupJobType(cleanupJobType);
        if (deleteFrom != null) {
            entry.setDeleteFrom(deleteFrom);
        }
        if (deleteTo != null) {
            entry.setDeleteTo(deleteTo);
        }
        entry.setDeletionTimestamp(ZonedDateTime.now());
        return this.cleanupJobExecutionRepository.save(entry);
    }
}
