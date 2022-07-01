package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.*;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.GroupGitDiffAndCoverageEntriesByFilePathAndTestCase;

class GroupGitDiffAndCoverageEntriesByFilePathAndTestCaseTest {

    private BehavioralBlackboard blackboard;

    private GroupGitDiffAndCoverageEntriesByFilePathAndTestCase grouper;

    @BeforeEach
    void initBlackboard() {
        blackboard = new BehavioralBlackboard(new ProgrammingExerciseGitDiffReport(), new CoverageReport(), null);
        blackboard.getGitDiffReport().setEntries(new HashSet<>());
        blackboard.getCoverageReport().setFileReports(new HashSet<>());

        grouper = new GroupGitDiffAndCoverageEntriesByFilePathAndTestCase(blackboard);
    }

    @Test
    void testNoAction() {
        blackboard.setGroupedFiles(Collections.emptyList());
        assertThat(grouper.executeCondition()).isFalse();
    }

    @Test
    void testNoFiles() {
        assertThat(grouper.executeCondition()).isTrue();
        assertThat(grouper.executeAction()).isFalse();
    }

    @Test
    void testGroupSingleFileAndTest() {
        var testCase = new ProgrammingExerciseTestCase();
        var gitDiffEntry1 = addGitDiffEntry("test.java", 1, 10);
        var gitDiffEntry2 = addGitDiffEntry("test.java", 12, 6);
        var coverageEntry1 = addCoverageEntry("test.java", 5, 5, testCase);
        var coverageEntry2 = addCoverageEntry("test.java", 15, 2, testCase);

        assertThat(grouper.executeCondition()).isTrue();
        assertThat(grouper.executeAction()).isTrue();
        assertThat(blackboard.getGroupedFiles()).isNotNull().hasSize(1)
                .containsExactly(new GroupedFile("test.java", testCase, Set.of(gitDiffEntry1, gitDiffEntry2), Set.of(coverageEntry1, coverageEntry2)));
    }

    @Test
    void testGroupTwoFilesSingleTest() {
        var testCase = new ProgrammingExerciseTestCase();
        var gitDiffEntry1 = addGitDiffEntry("test.java", 1, 20);
        var gitDiffEntry2 = addGitDiffEntry("test2.java", 1, 20);
        var coverageEntry1 = addCoverageEntry("test.java", 5, 5, testCase);
        var coverageEntry2 = addCoverageEntry("test2.java", 15, 2, testCase);

        assertThat(grouper.executeCondition()).isTrue();
        assertThat(grouper.executeAction()).isTrue();
        assertThat(blackboard.getGroupedFiles()).isNotNull().hasSize(2).containsExactlyInAnyOrder(
                new GroupedFile("test.java", testCase, Set.of(gitDiffEntry1), Set.of(coverageEntry1)),
                new GroupedFile("test2.java", testCase, Set.of(gitDiffEntry2), Set.of(coverageEntry2)));
    }

    @Test
    void testGroupSingleFileTwoTests() {
        var testCase1 = new ProgrammingExerciseTestCase();
        var testCase2 = new ProgrammingExerciseTestCase();
        var gitDiffEntry = addGitDiffEntry("test.java", 1, 20);
        var coverageEntry1 = addCoverageEntry("test.java", 5, 5, testCase1);
        var coverageEntry2 = addCoverageEntry("test.java", 15, 2, testCase2);

        assertThat(grouper.executeCondition()).isTrue();
        assertThat(grouper.executeAction()).isTrue();
        assertThat(blackboard.getGroupedFiles()).isNotNull().hasSize(2).containsExactlyInAnyOrder(
                new GroupedFile("test.java", testCase1, Set.of(gitDiffEntry), Set.of(coverageEntry1)),
                new GroupedFile("test.java", testCase2, Set.of(gitDiffEntry), Set.of(coverageEntry2)));
    }

    private ProgrammingExerciseGitDiffEntry addGitDiffEntry(String filePath, int startLine, int lineCount) {
        var gitDiffEntry = new ProgrammingExerciseGitDiffEntry();
        gitDiffEntry.setFilePath(filePath);
        gitDiffEntry.setStartLine(startLine);
        gitDiffEntry.setStartLine(lineCount);
        blackboard.getGitDiffReport().getEntries().add(gitDiffEntry);
        return gitDiffEntry;
    }

    private TestwiseCoverageReportEntry addCoverageEntry(String filePath, int startLine, int lineCount, ProgrammingExerciseTestCase testCase) {
        var coverageFileReport = blackboard.getCoverageReport().getFileReports().stream().filter(fileReport -> filePath.equals(fileReport.getFilePath())).findFirst()
                .orElse(new CoverageFileReport());
        coverageFileReport.setFilePath(filePath);
        if (coverageFileReport.getTestwiseCoverageEntries() == null) {
            coverageFileReport.setTestwiseCoverageEntries(new HashSet<>());
        }
        var coverageReportEntry = new TestwiseCoverageReportEntry();
        coverageReportEntry.setStartLine(startLine);
        coverageReportEntry.setLineCount(lineCount);
        coverageReportEntry.setTestCase(testCase);
        coverageFileReport.getTestwiseCoverageEntries().add(coverageReportEntry);
        blackboard.getCoverageReport().getFileReports().add(coverageFileReport);
        return coverageReportEntry;
    }
}
