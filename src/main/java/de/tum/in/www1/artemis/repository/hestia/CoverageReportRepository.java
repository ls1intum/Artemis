package de.tum.in.www1.artemis.repository.hestia;

import java.util.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.hestia.CoverageReport;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the CoverageReport entity.
 */
@Repository
public interface CoverageReportRepository extends JpaRepository<CoverageReport, Long> {

    default CoverageReport findByIdElseThrow(Long coverageReportId) {
        var optionalReport = findById(coverageReportId);
        return optionalReport.orElseThrow(() -> new EntityNotFoundException("Coverage Report", coverageReportId));
    }

    Boolean existsBySubmissionId(@Param("submissionId") Long submissionId);

    void deleteBySubmissionId(Long submissionId);

    @Query("""
            SELECT DISTINCT r FROM CoverageReport r
            LEFT JOIN FETCH r.submission s
            JOIN ProgrammingExercise pe
            ON s.participation = pe.solutionParticipation
            WHERE pe.id = :#{#programmingExerciseId}
            AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            order by s.submissionDate desc
            """)
    List<CoverageReport> getLatestCoverageReportsForLegalSubmissionsForProgrammingExercise(@Param("programmingExerciseId") Long programmingExerciseId, Pageable pageable);

    @Query("""
            SELECT DISTINCT r FROM CoverageReport r
            LEFT JOIN FETCH r.fileReports f
            LEFT JOIN FETCH f.testwiseCoverageEntries
            WHERE r.id = :#{#coverageReportId}
            """)
    Optional<CoverageReport> findCoverageReportByIdWithEagerFileReportsAndEntries(@Param("coverageReportId") Long coverageReportId);

    default CoverageReport findCoverageReportByIdWithEagerFileReportsAndEntriesElseThrow(Long coverageReportId) {
        var optionalReport = findCoverageReportByIdWithEagerFileReportsAndEntries(coverageReportId);
        return optionalReport.orElseThrow(() -> new EntityNotFoundException("Coverage Report", coverageReportId));
    }
}
