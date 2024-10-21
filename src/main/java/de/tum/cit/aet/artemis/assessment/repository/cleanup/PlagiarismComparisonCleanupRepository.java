package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;

/**
 * Spring Data JPA repository for cleaning up old plagiarism comparison entries.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Repository
public interface PlagiarismComparisonCleanupRepository extends ArtemisJpaRepository<PlagiarismComparison<?>, Long> {

    @Modifying
    @Transactional // ok because of delete
    void deleteByIdIn(List<Long> ids);

    /**
     * Retrieves a list of unnecessary plagiarism comparison IDs based on the associated course's date range.
     * A plagiarism comparison is considered unnecessary if its status is 'NONE' and the related course's
     * start and end dates fall within the provided range. Also deletes orphan objects.
     *
     * @param deleteFrom The start of the date range for filtering unnecessary plagiarism comparisons.
     * @param deleteTo   The end of the date range for filtering unnecessary plagiarism comparisons.
     * @return A list of Long values representing the IDs of unnecessary plagiarism comparisons.
     */
    @Query("""
            SELECT pc.id
            FROM PlagiarismComparison pc
            JOIN pc.plagiarismResult pr
            JOIN pr.exercise ex
            JOIN ex.course c
            WHERE pc.status = de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.NONE
                AND c.endDate < :deleteTo
                AND c.startDate > :deleteFrom
            """)
    List<Long> findPlagiarismComparisonIdWithStatusNoneThatBelongToCourseWithDates(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
