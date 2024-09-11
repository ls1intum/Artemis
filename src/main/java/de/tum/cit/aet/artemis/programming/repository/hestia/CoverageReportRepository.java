package de.tum.cit.aet.artemis.programming.repository.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
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

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.programming.domain.hestia.CoverageReport;
import de.tum.cit.aet.artemis.service.dto.CoverageReportAndSubmissionDateDTO;

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
            SELECT new de.tum.cit.aet.artemis.service.dto.CoverageReportAndSubmissionDateDTO(r, s.submissionDate)
            FROM CoverageReport r
                JOIN r.submission s
                JOIN ProgrammingExercise pe ON s.participation = pe.solutionParticipation
            WHERE pe.id = :programmingExerciseId
                AND (s.type <> de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            ORDER BY s.submissionDate DESC
            """)
    List<CoverageReportAndSubmissionDateDTO> findCoverageReportsByProgrammingExerciseId(@Param("programmingExerciseId") Long programmingExerciseId, Pageable pageable);

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
        List<Long> ids = findCoverageReportsByProgrammingExerciseId(programmingExerciseId, pageable).stream().map(CoverageReportAndSubmissionDateDTO::coverageReport)
                .map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
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
        List<Long> ids = findCoverageReportsByProgrammingExerciseId(programmingExerciseId, pageable).stream().map(CoverageReportAndSubmissionDateDTO::coverageReport)
                .map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
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
        return getValueElseThrow(findCoverageReportByIdWithEagerFileReportsAndEntries(coverageReportId), coverageReportId);
    }
}
