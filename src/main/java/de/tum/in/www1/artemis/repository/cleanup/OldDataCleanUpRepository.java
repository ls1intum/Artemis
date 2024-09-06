package de.tum.in.www1.artemis.repository.cleanup;

import java.time.ZonedDateTime;

public interface OldDataCleanUpRepository {

    void deleteOrphans();

    void deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    void deleteFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);
}
