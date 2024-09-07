package de.tum.in.www1.artemis.service.cleanup;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.cleanup.CleanupJobExecution;
import de.tum.in.www1.artemis.domain.enumeration.CleanupJobType;
import de.tum.in.www1.artemis.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.in.www1.artemis.repository.cleanup.DataCleanupRepository;
import de.tum.in.www1.artemis.web.rest.dto.CleanupServiceExecutionRecordDTO;

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
        return new CleanupServiceExecutionRecordDTO(this.createDBEntry(CleanupJobType.ORPHANS, null, null).getDeletionTimestamp());
    }

    public CleanupServiceExecutionRecordDTO deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deletePlagiarismComparisons(deleteFrom, deleteTo);
        return new CleanupServiceExecutionRecordDTO(this.createDBEntry(CleanupJobType.PLAGIARISM_COMPARISONS, deleteFrom, deleteTo).getDeletionTimestamp());
    }

    public CleanupServiceExecutionRecordDTO deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteNonRatedResults(deleteFrom, deleteTo);
        return new CleanupServiceExecutionRecordDTO(this.createDBEntry(CleanupJobType.NON_RATED_RESULTS, deleteFrom, deleteTo).getDeletionTimestamp());
    }

    public CleanupServiceExecutionRecordDTO deleteRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteRatedResults(deleteFrom, deleteTo);
        return new CleanupServiceExecutionRecordDTO(this.createDBEntry(CleanupJobType.RATED_RESULTS, deleteFrom, deleteTo).getDeletionTimestamp());
    }

    public CleanupServiceExecutionRecordDTO deleteSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteSubmissionVersions(deleteFrom, deleteTo);
        return new CleanupServiceExecutionRecordDTO(this.createDBEntry(CleanupJobType.SUBMISSION_VERSIONS, deleteFrom, deleteTo).getDeletionTimestamp());
    }

    public CleanupServiceExecutionRecordDTO deleteFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteFeedback(deleteFrom, deleteTo);
        return new CleanupServiceExecutionRecordDTO(this.createDBEntry(CleanupJobType.FEEDBACK, deleteFrom, deleteTo).getDeletionTimestamp());
    }

    public List<CleanupServiceExecutionRecordDTO> getLastExecutions() {
        List<CleanupServiceExecutionRecordDTO> executionRecords = new ArrayList<>();

        for (CleanupJobType jobType : CleanupJobType.values()) {
            CleanupJobExecution lastExecution = cleanupJobExecutionRepository.findTopByCleanupJobTypeOrderByDeletionTimestampDesc(jobType);
            if (lastExecution != null) {
                executionRecords.add(new CleanupServiceExecutionRecordDTO(lastExecution.getDeletionTimestamp()));
            }
            else {
                // Если задание еще ни разу не выполнялось, нужно добавить null
                executionRecords.add(new CleanupServiceExecutionRecordDTO(null));
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
