package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.BuildLogEntry;

/**
 * Spring Data JPA repository for the BuildLogEntry entity.
 */
@Repository
public interface BuildLogEntryRepository extends JpaRepository<BuildLogEntry, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteByProgrammingSubmissionId(long programmingSubmissionId);

}
