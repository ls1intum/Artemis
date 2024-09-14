package de.tum.cit.aet.artemis.core.repository.cleanup;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZonedDateTime;
import java.util.List;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

@Profile(PROFILE_CORE)
@Repository
public interface DataCleanupRepository {

    @Transactional
    void deleteOrphans();

    List<Long> getUnnecessaryPlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    @Transactional
    void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);

    @Transactional
    void deleteRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo);
}
