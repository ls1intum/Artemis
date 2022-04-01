package de.tum.in.www1.artemis.repository.hestia;

import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestwiseCoverageEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestwiseCoverageReport;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ProgrammingExerciseTestwiseCoverageReport entity.
 */
public interface ProgrammingExerciseTestwiseCoverageReportRepository extends JpaRepository<ProgrammingExerciseTestwiseCoverageReport, Long> {

    @Query("""
                SELECT DISTINCT r FROM ProgrammingExerciseTestwiseCoverageReport r
                LEFT JOIN FETCH r.testCase tc
                LEFT JOIN FETCH r.entries e
                WHERE tc.id = :#{#testCaseId}
            """)
    Optional<ProgrammingExerciseTestwiseCoverageReport> findByTestCaseId(@Value("testCaseId") Long testCaseId);

    default ProgrammingExerciseTestwiseCoverageReport findByTestCaseIdElseThrow(Long testCaseId) throws EntityNotFoundException {
        return findByTestCaseId(testCaseId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Testwise Coverage Report", testCaseId));
    }

    default Map<String, ProgrammingExerciseTestwiseCoverageReport> createTestwiseCoverageReportsWithoutTestsByTestCaseName(List<TestwiseCoverageReportDTO> coverageReports) {
        Map<String, ProgrammingExerciseTestwiseCoverageReport> reports = new HashMap<>();
        coverageReports.forEach(coveragePerTestDTO -> {
            Set<ProgrammingExerciseTestwiseCoverageEntry> coverageEntries = new HashSet<>();

            for (var pathDTO : coveragePerTestDTO.getCoveredPathsPerTestDTOS()) {
                for (var fileDTO : pathDTO.getCoveredFilesPerTestDTOS()) {
                    var coverageEntriesPerFile = Arrays.stream(fileDTO.getCoveredLinesWithRanges().split(",")).map(optionalLineRange -> {
                        String filePath = pathDTO.getPath() + "/" + fileDTO.getFileName();
                        int startLineNumber;
                        int linesCount;

                        if (optionalLineRange.contains("-")) {
                            String[] range = optionalLineRange.split("-");
                            startLineNumber = Integer.parseInt(range[0]);
                            linesCount = Integer.parseInt(range[1]) - startLineNumber + 1;
                        }
                        else {
                            startLineNumber = Integer.parseInt(optionalLineRange);
                            linesCount = 1;
                        }

                        var entry = new ProgrammingExerciseTestwiseCoverageEntry();
                        entry.setFilePath(filePath);
                        entry.setStartLine(startLineNumber);
                        entry.setLineCount(linesCount);
                        return entry;
                    }).toList();
                    coverageEntries.addAll(coverageEntriesPerFile);
                }
            }

            var report = new ProgrammingExerciseTestwiseCoverageReport();
            // extract the test case name from the uniformPath
            String[] split = coveragePerTestDTO.getUniformPath().split("/");
            String receivedTestCaseName = split[split.length - 1];

            // The test case is not set for the report because it has not been saved yet to the database
            report.setEntries(coverageEntries);
            reports.put(receivedTestCaseName, report);
        });

        return reports;
    }
}
