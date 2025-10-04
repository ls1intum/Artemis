package de.tum.cit.aet.artemis.core.config.migration.entries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
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
        long lastId = 0L;
        while (true) {
            var ids = resultRepository.findNextIds(lastId, PageRequest.of(0, 1000));
            if (ids.isEmpty()) {
                break;
            }
            long updatedCount = resultRepository.backfillExerciseIdBatch(ids);
            log.info("{} results updated", updatedCount);

            lastId = ids.getLast();
        }

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
