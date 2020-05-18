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

    @Query("select version from SubmissionVersion version left join version.submission submission where submission.id = :#{#submissionId} and version.id = (select max(id) from submission.versions)")
    Optional<SubmissionVersion> findLatestVersion(@Param("submissionId") long submissionId);

}
