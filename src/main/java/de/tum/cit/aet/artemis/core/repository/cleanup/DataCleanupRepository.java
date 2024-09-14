package de.tum.cit.aet.artemis.core.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
