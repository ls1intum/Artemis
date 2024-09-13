package de.tum.cit.aet.artemis.core.service.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.CleanupJobExecution;
import de.tum.cit.aet.artemis.core.domain.CleanupJobType;
import de.tum.cit.aet.artemis.core.dto.CleanupServiceExecutionRecordDTO;
import de.tum.cit.aet.artemis.core.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.cit.aet.artemis.core.repository.cleanup.DataCleanupRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;

@Profile(PROFILE_CORE)
@Service
public class DataCleanupService {

    private final DataCleanupRepository dataCleanUpRepository;

    private final CleanupJobExecutionRepository cleanupJobExecutionRepository;

    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    public DataCleanupService(DataCleanupRepository dataCleanUpRepository, CleanupJobExecutionRepository cleanupJobExecutionRepository,
            PlagiarismComparisonRepository plagiarismComparisonRepository) {
        this.dataCleanUpRepository = dataCleanUpRepository;
        this.cleanupJobExecutionRepository = cleanupJobExecutionRepository;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
    }

    public CleanupServiceExecutionRecordDTO deleteOrphans() {
        dataCleanUpRepository.deleteOrphans();
        return CleanupServiceExecutionRecordDTO.of(this.createCleanupJobExecution(CleanupJobType.ORPHANS, null, null));
    }

    @Transactional // transactinal ok, because of delete statements
    public CleanupServiceExecutionRecordDTO deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        var pcIds = dataCleanUpRepository.getUnnecessaryPlagiarismComparisons(deleteFrom, deleteTo);
        plagiarismComparisonRepository.deleteAllById(pcIds);
        return CleanupServiceExecutionRecordDTO.of(this.createCleanupJobExecution(CleanupJobType.PLAGIARISM_COMPARISONS, deleteFrom, deleteTo));
    }

    public CleanupServiceExecutionRecordDTO deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteNonRatedResults(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createCleanupJobExecution(CleanupJobType.NON_RATED_RESULTS, deleteFrom, deleteTo));
    }

    public CleanupServiceExecutionRecordDTO deleteRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        dataCleanUpRepository.deleteRatedResults(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createCleanupJobExecution(CleanupJobType.RATED_RESULTS, deleteFrom, deleteTo));
    }

    public List<CleanupServiceExecutionRecordDTO> getLastExecutions() {
        return Arrays.stream(CleanupJobType.values()).map(jobType -> {
            CleanupJobExecution lastExecution = cleanupJobExecutionRepository.findTopByCleanupJobTypeOrderByDeletionTimestampDesc(jobType);
            return lastExecution != null ? CleanupServiceExecutionRecordDTO.of(lastExecution) : new CleanupServiceExecutionRecordDTO(null, jobType.getName());
        }).collect(Collectors.toList());
    }

    private CleanupJobExecution createCleanupJobExecution(CleanupJobType cleanupJobType, ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
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
