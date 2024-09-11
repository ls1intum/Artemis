package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;

/**
 * Spring Data repository for the SubmissionVersion entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SubmissionVersionRepository extends ArtemisJpaRepository<SubmissionVersion, Long> {

    @Query("""
            SELECT version
            FROM SubmissionVersion version
                LEFT JOIN version.submission submission
                LEFT JOIN submission.versions
            WHERE submission.id = :submissionId
                AND version.id = (SELECT max(v.id) FROM submission.versions v)
            """)
    Optional<SubmissionVersion> findLatestVersion(@Param("submissionId") long submissionId);

    List<SubmissionVersion> findSubmissionVersionBySubmissionIdOrderByCreatedDateAsc(long submissionId);

}
