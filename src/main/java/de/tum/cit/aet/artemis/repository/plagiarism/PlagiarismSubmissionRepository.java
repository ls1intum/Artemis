package de.tum.cit.aet.artemis.repository.plagiarism;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the PlagiarismCase entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface PlagiarismSubmissionRepository extends ArtemisJpaRepository<PlagiarismSubmission<?>, Long> {

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE PlagiarismSubmission submission
            SET submission.plagiarismCase = :plagiarismCase
            WHERE submission.id = :submissionId
            """)
    void updatePlagiarismCase(@Param("submissionId") Long submissionId, @Param("plagiarismCase") PlagiarismCase plagiarismCase);
}
