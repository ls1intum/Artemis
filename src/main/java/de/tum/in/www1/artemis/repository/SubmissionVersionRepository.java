package de.tum.in.www1.artemis.repository;

import java.util.*;

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
            WHERE version.id = (
                SELECT max(version.id)
                FROM SubmissionVersion version
                WHERE version.submission.id = :submissionId
            )
            """)
    Optional<SubmissionVersion> findLatestVersion(@Param("submissionId") long submissionId);

    List<SubmissionVersion> findSubmissionVersionBySubmissionIdOrderByCreatedDateAsc(long submissionId);

}
