package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.ExtractCoveredLines;

class ExtractCoveredLinesTest {

    private ExtractCoveredLines extractCoveredLines;

    private GroupedFile groupedFile;

    private Set<TestwiseCoverageReportEntry> coverageReportEntries;

    @BeforeEach
    void initBlackboard() {
        BehavioralBlackboard blackboard = new BehavioralBlackboard(null, null, null);
        var groupedFiles = new ArrayList<GroupedFile>();
        blackboard.setGroupedFiles(groupedFiles);
        coverageReportEntries = new HashSet<>();
        groupedFile = new GroupedFile("test.java", null, null, coverageReportEntries);
        groupedFiles.add(groupedFile);

        extractCoveredLines = new ExtractCoveredLines(blackboard);
    }

    @Test
    void testNoAction() {
        groupedFile.setCoveredLines(Collections.emptySet());
        assertThat(extractCoveredLines.executeCondition()).isFalse();
    }

    @Test
    void testExtractChangedLines1() {
        var reportEntry = new TestwiseCoverageReportEntry();
        reportEntry.setStartLine(3);
        reportEntry.setLineCount(2);
        coverageReportEntries.add(reportEntry);

        assertThat(extractCoveredLines.executeCondition()).isTrue();
        assertThat(extractCoveredLines.executeAction()).isTrue();
        assertThat(groupedFile.getCoveredLines()).containsExactly(3, 4);
    }

    @Test
    void testExtractChangedLines2() {
        var reportEntry1 = new TestwiseCoverageReportEntry();
        reportEntry1.setStartLine(3);
        reportEntry1.setLineCount(2);
        var reportEntry2 = new TestwiseCoverageReportEntry();
        reportEntry2.setStartLine(6);
        reportEntry2.setLineCount(3);
        coverageReportEntries.add(reportEntry1);
        coverageReportEntries.add(reportEntry2);

        assertThat(extractCoveredLines.executeCondition()).isTrue();
        assertThat(extractCoveredLines.executeAction()).isTrue();
        assertThat(groupedFile.getCoveredLines()).containsExactly(3, 4, 6, 7, 8);
    }
}
