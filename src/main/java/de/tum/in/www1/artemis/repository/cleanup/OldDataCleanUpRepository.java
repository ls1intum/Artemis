package de.tum.in.www1.artemis.repository.cleanup;

import java.time.ZonedDateTime;

public interface OldDataCleanUpRepository {

    void deleteOrphans(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteOldRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteOldSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteOldFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    boolean existsDataForCleanup(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);
}
