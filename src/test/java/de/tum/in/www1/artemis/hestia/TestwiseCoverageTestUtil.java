package de.tum.in.www1.artemis.hestia;

import java.util.*;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TestwiseCoverageTestUtil {

    public static Map<String, Set<CoverageFileReport>> generateCoverageFileReportByTestName() {
        var fileReport1_1 = new CoverageFileReport();
        fileReport1_1.setFilePath("src/de/tum/in/ase/BubbleSort.java");
        var lineCountByStartLine1_1 = Map.ofEntries(Map.entry(15, 3), Map.entry(23, 1));
        var entries1_1 = lineCountByStartLine1_1.entrySet().stream().map((mapEntry) -> {
            var entry = new TestwiseCoverageReportEntry();
            entry.setStartLine(mapEntry.getKey());
            entry.setLineCount(mapEntry.getValue());
            return entry;
        }).collect(Collectors.toSet());
        fileReport1_1.setTestwiseCoverageEntries(entries1_1);

        var fileReport2_1 = new CoverageFileReport();
        fileReport2_1.setFilePath("src/de/tum/in/ase/BubbleSort.java");
        var lineCountByStartLine2_1 = Map.ofEntries(Map.entry(2, 1), Map.entry(16, 3));
        var entries2_1 = lineCountByStartLine2_1.entrySet().stream().map((mapEntry) -> {
            var entry = new TestwiseCoverageReportEntry();
            entry.setStartLine(mapEntry.getKey());
            entry.setLineCount(mapEntry.getValue());
            return entry;
        }).collect(Collectors.toSet());
        fileReport2_1.setTestwiseCoverageEntries(entries2_1);

        var fileReport2_2 = new CoverageFileReport();
        fileReport2_2.setFilePath("src/de/tum/in/ase/Context.java");
        var lineCountByStartLine2_2 = Map.ofEntries(Map.entry(1, 10));
        var entries2_2 = lineCountByStartLine2_2.entrySet().stream().map((mapEntry) -> {
            var entry = new TestwiseCoverageReportEntry();
            entry.setStartLine(mapEntry.getKey());
            entry.setLineCount(mapEntry.getValue());
            return entry;
        }).collect(Collectors.toSet());
        fileReport2_2.setTestwiseCoverageEntries(entries2_2);

        return Map.ofEntries(Map.entry("test1()", Set.of(fileReport1_1)), Map.entry("test2()", Set.of(fileReport2_1, fileReport2_2)));
    }

    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithCoverage() {
        var successfulTestNames = List.of("test1()", "test2()");
        var bambooNotification = ModelFactory.generateBambooBuildResult("SOLUTION", successfulTestNames, Collections.emptyList());

        // generate the coverage dto
        var linesByFileName1 = Map.ofEntries(Map.entry("BubbleSort.java", "15-17,23"));
        var fileDTOs1 = generateFileDTOs(linesByFileName1);
        var pathDTOs1 = generatePathDTOs(Map.ofEntries(Map.entry("de/tum/in/ase", fileDTOs1)));
        var testReportDTO1 = generateTestReport("de/tum/in/ase/SortingExampleBehaviorTest/test1()", "", 0.022, pathDTOs1);
        var linesByFileName2 = Map.ofEntries(Map.entry("BubbleSort.java", "2,16-18"), Map.entry("Context.java", "1-10"));
        var fileDTOs2 = generateFileDTOs(linesByFileName2);
        var pathDTOs2 = generatePathDTOs(Map.ofEntries(Map.entry("de/tum/in/ase", fileDTOs2)));
        var testReportDTO2 = generateTestReport("de/tum/in/ase/SortingExampleBehaviorTest/test2()", "", 0.022, pathDTOs2);

        bambooNotification.getBuild().getJobs().stream().findFirst().get().setTestwiseCoverageReports(List.of(testReportDTO1, testReportDTO2));
        return bambooNotification;
    }

    private static List<TestwiseCoverageReportDTO.CoveredFilesPerTestDTO> generateFileDTOs(Map<String, String> linesByFileName) {
        var result = new ArrayList<TestwiseCoverageReportDTO.CoveredFilesPerTestDTO>();
        linesByFileName.forEach((fileName, lines) -> {
            var coveredFile = new TestwiseCoverageReportDTO.CoveredFilesPerTestDTO();
            coveredFile.setFileName(fileName);
            coveredFile.setCoveredLinesWithRanges(lines);
            result.add(coveredFile);
        });
        return result;
    }

    private static List<TestwiseCoverageReportDTO.CoveredPathsPerTestDTO> generatePathDTOs(Map<String, List<TestwiseCoverageReportDTO.CoveredFilesPerTestDTO>> filesByPath) {
        var result = new ArrayList<TestwiseCoverageReportDTO.CoveredPathsPerTestDTO>();
        filesByPath.forEach((path, fileEntries) -> {
            var coveredPath = new TestwiseCoverageReportDTO.CoveredPathsPerTestDTO();
            coveredPath.setPath(path);
            coveredPath.setCoveredFilesPerTestDTOs(fileEntries);
            result.add(coveredPath);
        });
        return result;
    }

    private static TestwiseCoverageReportDTO generateTestReport(String uniformPath, String content, double duration,
            List<TestwiseCoverageReportDTO.CoveredPathsPerTestDTO> pathEntries) {
        var result = new TestwiseCoverageReportDTO();
        result.setUniformPath(uniformPath);
        result.setContent(content);
        result.setDuration(duration);
        result.setCoveredPathsPerTestDTOs(pathEntries);
        return result;
    }
}
