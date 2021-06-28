package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.BuildLogEntry;

/**
 * Spring Data JPA repository for the BuildLogEntry entity.
 */
@Repository
public interface BuildLogEntryRepository extends JpaRepository<BuildLogEntry, Long> {

    /**
     * deletes all BuildLogEntry s of a ProgrammingSubmission
     *
     * @param programmingSubmissionId the id of the submission
     */
    @Modifying
    @Query("""
            DELETE
            FROM BuildLogEntry b
            WHERE b.programmingSubmission.id = :programmingSubmissionId
            """)
    void deleteAllByProgrammingSubmissionId(@Param("programmingSubmissionId") Long programmingSubmissionId);

}
