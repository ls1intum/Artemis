package de.tum.in.www1.artemis.repository.cleanup;

import java.time.ZonedDateTime;

public interface OldDataCleanUpRepository {

    void deleteOrphans();

    void deletePlagiarismComparisons(ZonedDateTime keepAllLaterThan);

    void deleteNonRatedResults(ZonedDateTime keepAllLaterThan);

    void deleteOldRatedResults(ZonedDateTime keepAllLaterThan);

    void deleteOldSubmissionVersions(ZonedDateTime keepAllLaterThan);

    void deleteOldFeedback(ZonedDateTime keepAllLaterThan);

    boolean existsDataForCleanup(ZonedDateTime keepAllLaterThan);
}
