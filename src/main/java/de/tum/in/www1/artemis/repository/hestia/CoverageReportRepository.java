package de.tum.in.www1.artemis.repository.hestia;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.hestia.CoverageReport;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the CoverageReport entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CoverageReportRepository extends ArtemisJpaRepository<CoverageReport, Long> {

    Boolean existsBySubmissionId(Long submissionId);

    @Transactional // ok because of delete
    @Modifying
    void deleteBySubmissionId(Long submissionId);

    @Query("""
            SELECT DISTINCT r
            FROM CoverageReport r
                LEFT JOIN FETCH r.submission s
                JOIN ProgrammingExercise pe ON s.participation = pe.solutionParticipation
            WHERE pe.id = :programmingExerciseId
                AND (s.type <> de.tum.in.www1.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            ORDER BY s.submissionDate DESC
            """)
    // TODO: rewrite this query, pageable does not work well with left join fetch
    List<CoverageReport> getLatestCoverageReportsForLegalSubmissionsForProgrammingExercise(@Param("programmingExerciseId") Long programmingExerciseId, Pageable pageable);

    @Query("""
            SELECT DISTINCT r
            FROM CoverageReport r
                LEFT JOIN FETCH r.submission s
                LEFT JOIN FETCH r.fileReports f
                LEFT JOIN FETCH f.testwiseCoverageEntries
                JOIN ProgrammingExercise pe ON s.participation = pe.solutionParticipation
            WHERE pe.id = :programmingExerciseId
                AND (s.type <> de.tum.in.www1.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            ORDER BY s.submissionDate DESC
            """)
    // TODO: rewrite this query, pageable does not work well with left join fetch, it needs to transfer all results and only page in java
    List<CoverageReport> getLatestCoverageReportsForLegalSubmissionsForProgrammingExerciseWithEagerFileReportsAndEntries(@Param("programmingExerciseId") Long programmingExerciseId,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT r
            FROM CoverageReport r
                LEFT JOIN FETCH r.fileReports f
                LEFT JOIN FETCH f.testwiseCoverageEntries
            WHERE r.id = :coverageReportId
            """)
    Optional<CoverageReport> findCoverageReportByIdWithEagerFileReportsAndEntries(@Param("coverageReportId") Long coverageReportId);

    default CoverageReport findCoverageReportByIdWithEagerFileReportsAndEntriesElseThrow(Long coverageReportId) {
        var optionalReport = findCoverageReportByIdWithEagerFileReportsAndEntries(coverageReportId);
        return optionalReport.orElseThrow(() -> new EntityNotFoundException("Coverage Report", coverageReportId));
    }
}
