package de.tum.cit.aet.artemis.plagiarism.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparisonSide;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;

/**
 * Spring Data JPA repository for the PlagiarismCase entity.
 */
@Conditional(PlagiarismEnabled.class)
@Lazy
@Repository
public interface PlagiarismSubmissionRepository extends ArtemisJpaRepository<PlagiarismSubmission, Long> {

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE PlagiarismSubmission submission
            SET submission.plagiarismCase = :plagiarismCase
            WHERE submission.id = :submissionId
            """)
    void updatePlagiarismCase(@Param("submissionId") Long submissionId, @Param("plagiarismCase") PlagiarismCase plagiarismCase);

    /**
     * One side of a comparison with its elements loaded. The two sides are read one at a time because their element
     * lists are bags, and a single query cannot fetch two of those.
     *
     * @param comparisonId the comparison the submission belongs to
     * @param side         which of the two submissions of the comparison to read
     * @return the submission, if the comparison has one on that side
     */
    @Query("""
            SELECT submission
            FROM PlagiarismSubmission submission
                LEFT JOIN FETCH submission.elements
            WHERE submission.plagiarismComparison.id = :comparisonId
                AND submission.side = :side
            """)
    Optional<PlagiarismSubmission> findWithElementsByComparisonIdAndSide(@Param("comparisonId") long comparisonId, @Param("side") PlagiarismComparisonSide side);
}
