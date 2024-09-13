package de.tum.in.www1.artemis.service.cleanup;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.cleanup.CleanupJobExecution;
import de.tum.in.www1.artemis.domain.enumeration.CleanupJobType;
import de.tum.in.www1.artemis.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.in.www1.artemis.repository.cleanup.DataCleanupRepository;
import de.tum.in.www1.artemis.web.rest.dto.CleanupServiceExecutionRecordDTO;
import org.springframework.transaction.annotation.Transactional;

@Profile(PROFILE_CORE)
@Service
public class DataCleanupService {

    private final DataCleanupRepository dataCleanUpRepository;

    private final CleanupJobExecutionRepository cleanupJobExecutionRepository;

    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    public DataCleanupService(DataCleanupRepository dataCleanUpRepository, CleanupJobExecutionRepository cleanupJobExecutionRepository, PlagiarismComparisonRepository plagiarismComparisonRepository) {
        this.dataCleanUpRepository = dataCleanUpRepository;
        this.cleanupJobExecutionRepository = cleanupJobExecutionRepository;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
    }

    public CleanupServiceExecutionRecordDTO deleteOrphans() {
        dataCleanUpRepository.deleteOrphans();
        return CleanupServiceExecutionRecordDTO.of(this.createDBEntry(CleanupJobType.ORPHANS, null, null));
    }

    @Transactional // transactinal ok, because of delete statements
    public CleanupServiceExecutionRecordDTO deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        var pcIds = dataCleanUpRepository.getUnnecessaryPlagiarismComparisons(deleteFrom, deleteTo);
        plagiarismComparisonRepository.deleteAllById(pcIds);
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

    public List<CleanupServiceExecutionRecordDTO> getLastExecutions() {
        return Arrays.stream(CleanupJobType.values())
            .map(jobType -> {
                CleanupJobExecution lastExecution = cleanupJobExecutionRepository.findTopByCleanupJobTypeOrderByDeletionTimestampDesc(jobType);
                return lastExecution != null ? CleanupServiceExecutionRecordDTO.of(lastExecution) : new CleanupServiceExecutionRecordDTO(null, jobType.getName());
            })
            .collect(Collectors.toList());
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
