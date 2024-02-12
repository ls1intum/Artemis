package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.SubmissionVersion;

/**
 * Spring Data repository for the SubmissionVersion entity.
 */
@Profile(PROFILE_CORE)
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

    List<SubmissionVersion> findSubmissionVersionBySubmissionIdOrderByCreatedDateAsc(long submissionId);

}
