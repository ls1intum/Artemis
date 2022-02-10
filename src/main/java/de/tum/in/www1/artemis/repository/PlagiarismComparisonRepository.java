package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the PlagiarismComparison entity.
 */
@Repository
public interface PlagiarismComparisonRepository extends JpaRepository<PlagiarismComparison<?>, Long> {

    default PlagiarismComparison<?> findByIdElseThrow(long comparisonId) {
        return findById(comparisonId).orElseThrow(() -> new EntityNotFoundException("PlagiarismComparison", comparisonId));
    }

    Optional<Set<PlagiarismComparison<?>>> findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(long submissionA_submissionId, long submissionB_submissionId);

    // we can't simply call save() on plagiarismComparisons because the plagiarismComparisonMatches have no id
    // and would be recreated. Therefore, we need some update methods:

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE PlagiarismComparison plagiarismComparison set plagiarismComparison.status = :status where plagiarismComparison.id = :plagiarismComparisonId")
    void updatePlagiarismComparisonStatus(@Param("plagiarismComparisonId") Long plagiarismComparisonId, @Param("status") PlagiarismStatus status);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE PlagiarismComparison plagiarismComparison set plagiarismComparison.statusA = :status where plagiarismComparison.id = :plagiarismComparisonId")
    void updatePlagiarismComparisonFinalStatusA(@Param("plagiarismComparisonId") Long plagiarismComparisonId, @Param("status") PlagiarismStatus status);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE PlagiarismComparison plagiarismComparison set plagiarismComparison.statusB = :status where plagiarismComparison.id = :plagiarismComparisonId")
    void updatePlagiarismComparisonFinalStatusB(@Param("plagiarismComparisonId") Long plagiarismComparisonId, @Param("status") PlagiarismStatus status);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE PlagiarismComparison plagiarismComparison set plagiarismComparison.instructorStatementA = :statement where plagiarismComparison.id = :plagiarismComparisonId")
    void updatePlagiarismComparisonInstructorStatementA(@Param("plagiarismComparisonId") Long plagiarismComparisonId, @Param("statement") String statement);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE PlagiarismComparison plagiarismComparison set plagiarismComparison.instructorStatementB = :statement where plagiarismComparison.id = :plagiarismComparisonId")
    void updatePlagiarismComparisonInstructorStatementB(@Param("plagiarismComparisonId") Long plagiarismComparisonId, @Param("statement") String statement);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE PlagiarismComparison plagiarismComparison set plagiarismComparison.studentStatementA = :statement where plagiarismComparison.id = :plagiarismComparisonId")
    void updatePlagiarismComparisonStudentStatementA(@Param("plagiarismComparisonId") Long plagiarismComparisonId, @Param("statement") String statement);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE PlagiarismComparison plagiarismComparison set plagiarismComparison.studentStatementB = :statement where plagiarismComparison.id = :plagiarismComparisonId")
    void updatePlagiarismComparisonStudentStatementB(@Param("plagiarismComparisonId") Long plagiarismComparisonId, @Param("statement") String statement);

}
