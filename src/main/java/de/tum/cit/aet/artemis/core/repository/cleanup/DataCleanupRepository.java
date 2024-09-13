package de.tum.cit.aet.artemis.core.repository.cleanup;

import java.time.ZonedDateTime;
import java.util.Collection;

public interface DataCleanupRepository {

    void deleteOrphans();

    Collection<Long> getUnnecessaryPlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);
}
