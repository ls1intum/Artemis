package de.tum.cit.aet.artemis.plagiarism.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
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
     * The commit hashes that were compared for a participation of the exercise, newest comparison first.
     * Only programming comparisons carry a hash, and their compared ids are participation ids.
     *
     * @param exerciseId      the exercise the comparison belongs to
     * @param participationId the id of the participation, which is the compared id of a programming comparison
     * @param limit           the maximum number of rows, one row is enough for the newest hash
     * @return the stored commit hashes, newest first
     */
    @Query("""
            SELECT submission.commitHash
            FROM PlagiarismSubmission submission
                JOIN submission.plagiarismComparison comparison
                JOIN comparison.plagiarismResult result
            WHERE result.exercise.id = :exerciseId
                AND submission.submissionId = :participationId
                AND submission.commitHash IS NOT NULL
            ORDER BY submission.id DESC
            """)
    List<String> findComparedCommitHashes(@Param("exerciseId") long exerciseId, @Param("participationId") long participationId, Pageable limit);

    /**
     * The creation dates of the plagiarism results that compared a participation of the exercise, newest first.
     *
     * @param exerciseId      the exercise the comparison belongs to
     * @param participationId the id of the participation, which is the compared id of a programming comparison
     * @param limit           the maximum number of rows
     * @return the creation dates of the results, newest first
     */
    @Query("""
            SELECT result.createdDate
            FROM PlagiarismSubmission submission
                JOIN submission.plagiarismComparison comparison
                JOIN comparison.plagiarismResult result
            WHERE result.exercise.id = :exerciseId
                AND submission.submissionId = :participationId
            ORDER BY result.createdDate DESC, submission.id DESC
            """)
    List<Instant> findComparisonCreatedDates(@Param("exerciseId") long exerciseId, @Param("participationId") long participationId, Pageable limit);

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
