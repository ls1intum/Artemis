package de.tum.in.www1.artemis.repository.hestia;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.hestia.CoverageReport;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

record CoverageReportAndSubmissionDate(CoverageReport coverageReport, ZonedDateTime submissionDate) {

    public CoverageReportAndSubmissionDate(CoverageReport coverageReport, ZonedDateTime submissionDate) {
        this.coverageReport = coverageReport;
        this.submissionDate = submissionDate;
    }
}

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
            SELECT new de.tum.in.www1.artemis.repository.hestia.CoverageReportAndSubmissionDate(r, s.submissionDate)
            FROM CoverageReport r
            JOIN r.submission s
            JOIN ProgrammingExercise pe ON s.participation = pe.solutionParticipation
            WHERE pe.id = :programmingExerciseId
            AND (s.type <> de.tum.in.www1.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            ORDER BY s.submissionDate DESC
            """)
    List<CoverageReportAndSubmissionDate> findCoverageReportsByProgrammingExerciseId(@Param("programmingExerciseId") Long programmingExerciseId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    List<CoverageReport> findCoverageReportsWithSubmissionByIdIn(List<Long> ids);

    /**
     * Retrieves the latest coverage reports with legal submissions for a specific programming exercise, with pagination support.
     * This method avoids in-memory paging by retrieving the coverage report IDs directly from the database.
     *
     * @param programmingExerciseId the ID of the programming exercise to retrieve the coverage reports for
     * @param pageable              the pagination information
     * @return a list of {@code CoverageReport} with legal submissions, or an empty list if no reports are found
     */
    default List<CoverageReport> getLatestCoverageReportsWithLegalSubmissionsForProgrammingExercise(Long programmingExerciseId, Pageable pageable) {
        List<Long> ids = findCoverageReportsByProgrammingExerciseId(programmingExerciseId, pageable).stream().map(CoverageReportAndSubmissionDate::coverageReport)
                .map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return findCoverageReportsWithSubmissionByIdIn(ids);
    }

    @EntityGraph(type = LOAD, attributePaths = { "submission", "fileReports", "fileReports.testwiseCoverageEntries" })
    List<CoverageReport> findDistinctCoverageReportsWithEagerRelationshipsByIdIn(List<Long> ids);

    /**
     * Retrieves the latest coverage reports with legal submissions for a specific programming exercise, including eager loading of file reports and entries, with pagination
     * support.
     * This method avoids in-memory paging by retrieving the coverage report IDs directly from the database.
     *
     * @param programmingExerciseId the ID of the programming exercise to retrieve the coverage reports for
     * @param pageable              the pagination information
     * @return a list of distinct {@code CoverageReport} with eager relationships, or an empty list if no reports are found
     */
    default List<CoverageReport> getLatestCoverageReportsForLegalSubmissionsForProgrammingExerciseWithEagerFileReportsAndEntries(Long programmingExerciseId, Pageable pageable) {
        List<Long> ids = findCoverageReportsByProgrammingExerciseId(programmingExerciseId, pageable).stream().map(CoverageReportAndSubmissionDate::coverageReport)
                .map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return findDistinctCoverageReportsWithEagerRelationshipsByIdIn(ids);
    }

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
