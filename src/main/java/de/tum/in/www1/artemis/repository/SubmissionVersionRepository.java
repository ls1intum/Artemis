package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.SubmissionVersion;

/**
 * Spring Data repository for the SubmissionVersion entity.
 */
@Repository
public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, Long> {

    @Query("""
            SELECT version
            FROM SubmissionVersion version
                LEFT JOIN version.submission submission
                LEFT JOIN submission.versions
             WHERE
                submission.id = :submissionId and version.id = (
                    SELECT max(id)
                    FROM submission.versions
                    )
            """)
    Optional<SubmissionVersion> findLatestVersion(@Param("submissionId") long submissionId);

}
