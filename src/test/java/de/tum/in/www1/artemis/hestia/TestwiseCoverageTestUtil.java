package de.tum.in.www1.artemis.hestia;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;

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

}
