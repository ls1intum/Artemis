package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;

class GroupedFile {

    private final String filePath;

    private final ProgrammingExerciseTestCase testCase;

    private final Set<ProgrammingExerciseGitDiffEntry> gitDiffEntries;

    private final Set<TestwiseCoverageReportEntry> coverageReportEntries;

    private Set<Integer> changedLines;

    private Set<Integer> coveredLines;

    private List<Integer> commonLines;

    public GroupedFile(String filePath, ProgrammingExerciseTestCase testCase, Set<ProgrammingExerciseGitDiffEntry> gitDiffEntries,
            Set<TestwiseCoverageReportEntry> coverageReportEntries) {
        this.filePath = filePath;
        this.testCase = testCase;
        this.gitDiffEntries = gitDiffEntries;
        this.coverageReportEntries = coverageReportEntries;
    }

    public String getFilePath() {
        return filePath;
    }

    public ProgrammingExerciseTestCase getTestCase() {
        return testCase;
    }

    public Set<ProgrammingExerciseGitDiffEntry> getGitDiffEntries() {
        return gitDiffEntries;
    }

    public Set<TestwiseCoverageReportEntry> getCoverageReportEntries() {
        return coverageReportEntries;
    }

    public Set<Integer> getChangedLines() {
        return changedLines;
    }

    public void setChangedLines(Set<Integer> changedLines) {
        this.changedLines = changedLines;
    }

    public Set<Integer> getCoveredLines() {
        return coveredLines;
    }

    public void setCoveredLines(Set<Integer> coveredLines) {
        this.coveredLines = coveredLines;
    }

    public List<Integer> getCommonLines() {
        return commonLines;
    }

    public void setCommonLines(List<Integer> commonLines) {
        this.commonLines = commonLines;
    }
}
