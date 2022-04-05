package de.tum.in.www1.artemis.repository.hestia;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;
import de.tum.in.www1.artemis.domain.hestia.CoverageReport;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;
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

    Optional<CoverageReport> findCoverageReportBySubmissionId(@Param("submissionId") Long submissionId);

    default CoverageReport findCoverageReportBySubmissionIdElseThrow(Long submissionId) {
        var optionalReport = findCoverageReportBySubmissionId(submissionId);
        return optionalReport.orElseThrow(() -> new EntityNotFoundException("Coverage Report", submissionId));
    }

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

    /**
     * Transforms the testwise coverage report dtos to CoverageFileReports (without the test case attribute) mapped by the test case name.
     * This method maps the file reports to primitive test case names because the test case is not present in the database
     * on creating the entities from the dtos.
     * @param coverageReports the coverage report dtos
     * @return coverage file reports mapped by the test case name
     */
    default Map<String, Set<CoverageFileReport>> createTestwiseCoverageFileReportsWithoutTestsByTestCaseName(List<TestwiseCoverageReportDTO> coverageReports) {
        Map<String, Set<CoverageFileReport>> fileReportsByTestName = new HashMap<>();
        coverageReports.forEach(coveragePerTestDTO -> {

            for (var pathDTO : coveragePerTestDTO.getCoveredPathsPerTestDTOS()) {

                // the file reports for the current test case
                Set<CoverageFileReport> fileCoverageReports = new HashSet<>();
                for (var fileDTO : pathDTO.getCoveredFilesPerTestDTOS()) {
                    var coverageEntriesPerFile = Arrays.stream(fileDTO.getCoveredLinesWithRanges().split(",")).map(optionalLineRange -> {
                        int startLineNumber;
                        int linesCount;

                        // retrieve consecutive blocks from the ranged covered lines number
                        // Example: "2,3-6,7,9-30"
                        if (optionalLineRange.contains("-")) {
                            String[] range = optionalLineRange.split("-");
                            startLineNumber = Integer.parseInt(range[0]);
                            linesCount = Integer.parseInt(range[1]) - startLineNumber + 1;
                        }
                        else {
                            startLineNumber = Integer.parseInt(optionalLineRange);
                            linesCount = 1;
                        }

                        // The test case is not set for the report because it has not been saved yet to the database
                        var entry = new TestwiseCoverageReportEntry();
                        entry.setStartLine(startLineNumber);
                        entry.setLineCount(linesCount);
                        return entry;
                    }).collect(Collectors.toSet());

                    // build the file report with the entries for this specific file
                    var fileReport = new CoverageFileReport();
                    // 'src/' needs to be prepended to match the repositories' relative file path
                    String filePath = "src/" + pathDTO.getPath() + "/" + fileDTO.getFileName();
                    fileReport.setFilePath(filePath);
                    fileReport.setTestwiseCoverageEntries(coverageEntriesPerFile);
                    fileCoverageReports.add(fileReport);
                }

                // extract the test case name from the uniformPath
                String[] split = coveragePerTestDTO.getUniformPath().split("/");
                String receivedTestCaseName = split[split.length - 1];
                fileReportsByTestName.put(receivedTestCaseName, fileCoverageReports);
            }
        });

        return fileReportsByTestName;
    }

}
