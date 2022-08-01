package de.tum.in.www1.artemis.repository.plagiarism;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;

/**
 * Spring Data JPA repository for the PlagiarismCase entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PlagiarismSubmissionRepository extends JpaRepository<PlagiarismSubmission<?>, Long> {

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE PlagiarismSubmission submission set submission.plagiarismCase = :#{#plagiarismCase} where submission.id = :#{#submissionId}")
    void updatePlagiarismCase(@Param("submissionId") Long submissionId, @Param("plagiarismCase") PlagiarismCase plagiarismCase);
}
