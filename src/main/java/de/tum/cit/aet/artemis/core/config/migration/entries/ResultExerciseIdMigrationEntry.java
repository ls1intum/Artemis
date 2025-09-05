package de.tum.cit.aet.artemis.core.config.migration.entries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.config.migration.MigrationEntry;

@Component
public class ResultExerciseIdMigrationEntry extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(ResultExerciseIdMigrationEntry.class);

    private final ResultRepository resultRepository;

    public ResultExerciseIdMigrationEntry(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    @Override
    public void execute() {
        Slice<Long> resultIds;
        do {
            resultIds = resultRepository.findResultIdsWithoutExerciseId(Pageable.ofSize(500));
            log.info("Backfilling exerciseId for {} results", resultIds.getNumberOfElements());
            var updatedCount = resultRepository.backfillExerciseIdBatch(resultIds.getContent());
            log.info("Backfilled exerciseId for {} results", updatedCount);
        }
        while (resultIds.hasNext());

    }

    @Override
    public String author() {
        return "tobias-lippert";
    }

    @Override
    public String date() {
        return "2025-09-01";
    }
}
