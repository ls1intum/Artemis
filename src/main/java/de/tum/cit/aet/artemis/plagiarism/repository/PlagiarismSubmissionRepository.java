package de.tum.cit.aet.artemis.plagiarism.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;

/**
 * Spring Data JPA repository for the PlagiarismCase entity.
 */
@Conditional(PlagiarismEnabled.class)
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
