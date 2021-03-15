package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;

/**
 * Spring Data JPA repository for the PlagiarismComparison entity.
 */
@Repository
public interface PlagiarismComparisonRepository extends JpaRepository<PlagiarismComparison, Long> {

    @Modifying
    @Query("UPDATE PlagiarismComparison plagiarismComparison set plagiarismComparison.status = :status where plagiarismComparison.id = :id")
    void updatePlagiarismComparisonStatus(@Param("id") Long id, @Param("status") PlagiarismStatus status);

}
