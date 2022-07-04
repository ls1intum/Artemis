package de.tum.in.www1.artemis.repository.hestia;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;

@Repository
public interface CoverageFileReportRepository extends JpaRepository<CoverageFileReport, Long> {

    Set<CoverageFileReport> findCoverageFileReportByFullReportId(@Param("coverageReportId") Long coverageReportId);
}
