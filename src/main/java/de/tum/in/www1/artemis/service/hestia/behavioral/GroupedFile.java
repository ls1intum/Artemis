package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.*;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;

class GroupedFile {

    private final String filePath;

    private final ProgrammingExerciseTestCase testCase;

    private final Set<ProgrammingExerciseGitDiffEntry> gitDiffEntries;

    private final Set<TestwiseCoverageReportEntry> coverageReportEntries;

    private String fileContent;

    private Set<Integer> changedLines;

    private Set<Integer> coveredLines;

    private SortedSet<Integer> commonLines;

    private SortedSet<ChangeBlock> commonChanges;

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

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
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

    public SortedSet<Integer> getCommonLines() {
        return commonLines;
    }

    public void setCommonLines(Collection<Integer> commonLines) {
        this.commonLines = new TreeSet<>(commonLines);
    }

    public SortedSet<ChangeBlock> getCommonChanges() {
        return commonChanges;
    }

    public void setCommonChanges(Collection<ChangeBlock> commonChanges) {
        this.commonChanges = new TreeSet<>(commonChanges);
    }

    public static class ChangeBlock implements Comparable<ChangeBlock> {

        private SortedSet<Integer> lines;

        private boolean isPotential;

        public ChangeBlock(Collection<Integer> lines) {
            this.lines = new TreeSet<>(lines);
            this.isPotential = false;
        }

        public ChangeBlock(Collection<Integer> lines, boolean isPotential) {
            this.lines = new TreeSet<>(lines);
            this.isPotential = isPotential;
        }

        public boolean intersectsOrTouches(GroupedFile.ChangeBlock other) {
            return (this.lines.first() > other.lines.first() && this.lines.first() <= other.lines.last() + 1)
                    || (this.lines.first() < other.lines.first() && this.lines.last() >= other.lines.first() - 1);
        }

        @Override
        public int compareTo(GroupedFile.ChangeBlock other) {
            return this.lines.first().compareTo(other.lines.first());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            ChangeBlock that = (ChangeBlock) o;
            return isPotential == that.isPotential && lines.equals(that.lines);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lines, isPotential);
        }

        public void setLines(Collection<Integer> lines) {
            this.lines = new TreeSet<>(lines);
        }

        public SortedSet<Integer> getLines() {
            return lines;
        }

        public boolean isPotential() {
            return isPotential;
        }

        public void setPotential(boolean potential) {
            isPotential = potential;
        }
    }
}
